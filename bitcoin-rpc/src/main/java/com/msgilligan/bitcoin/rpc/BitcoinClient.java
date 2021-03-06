package com.msgilligan.bitcoin.rpc;


import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.RegTestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-RPC Client for bitcoind
 */
public class BitcoinClient extends RPCClient {
    private static final Logger log = LoggerFactory.getLogger(BitcoinClient.class);

    private static final Integer SECOND = 1000;

    public BitcoinClient(URI server, String rpcuser, String rpcpassword) {
        super(server, rpcuser, rpcpassword);
    }

    public BitcoinClient(RPCConfig config) throws IOException {
        this(config.getURI(), config.getUsername(), config.getPassword());
    }

    /**
     *
     * @param timeout Timeout in seconds
     * @return true if ready, false if timeout
     */
    public Boolean waitForServer(Integer timeout) throws JsonRPCException {
        Integer seconds = 0;

        log.debug("Waiting for server RPC ready:");

        Integer block;

        while ( seconds < timeout ) {
            try {
                block = this.getBlockCount();
                if (block != null ) {
                    log.debug("\nRPC Ready.");
                    return true;
                }
            } catch (SocketException se ) {
                // These are expected exceptions while waiting for a server
                if (! ( se.getMessage().equals("Unexpected end of file from server") ||
                        se.getMessage().equals("Connection reset") ||
                        se.getMessage().equals("Connection refused") ||
                        se.getMessage().equals("recvfrom failed: ECONNRESET (Connection reset by peer)"))) {
                    se.printStackTrace();
                }

            } catch (java.io.EOFException e) {
                /* Android exception, ignore */
                // Expected exceptions on Android, RoboVM
            } catch (JsonRPCException e) {
                e.printStackTrace();
                throw e;
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                System.err.print(".");      // Every second print a '.'
                seconds++;
                if (seconds % 60 == 0) {    // Every minute start a new line
                    System.err.println();
                }
                Thread.sleep(SECOND);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Wait for RPC server to reach specified block height
     *
     * @param blockHeight blockHeight to wait for
     * @param timeout Timeout in seconds
     * @return true if blockHeight reached, false if timeout
     */
    public Boolean waitForBlock(Integer blockHeight, Integer timeout) throws JsonRPCException, IOException {
        Integer seconds = 0;

        log.info("Waiting for server to reach block " + blockHeight);

        Integer block;

        while ( seconds < timeout ) {
            block = this.getBlockCount();
            if (block >= blockHeight ) {
                log.info("Server is at block " + block + " returning 'true'.");
                return true;
            } else {
                try {
                    seconds++;
                    if (seconds % 60 == 0) {
                        log.info("Server at block " + block);
                    }
                    Thread.sleep(SECOND);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
        return false;
    }

    /**
     *
     * @return current block height (count)
     * @throws IOException
     */
    public Integer getBlockCount() throws JsonRPCException, IOException {
        Integer blockCount = send("getblockcount", null);
        return blockCount;
    }

    /**
     * Returns the hash of block in best-block-chain at index provided.
     *
     * @param index The block index
     * @return The block hash
     */
    public Sha256Hash getBlockHash(Integer index) throws JsonRPCException, IOException {
        List<Object> params = createParamList(index);
        String hashStr = send("getblockhash", params);
        Sha256Hash hash = new Sha256Hash(hashStr);
        return hash;
    }

    /**
     * Returns information about a block with the given block hash.
     *
     * @param hash The block hash
     * @return The information about the block
     */
    public Map<String,Object> getBlock(Sha256Hash hash) throws JsonRPCException, IOException {
        // Use "verbose = true"
        List<Object> params = createParamList(hash.toString(), true);
        Map<String, Object> json = send("getblock", params);
        return json;
    }

    /**
     * Returns information about a block at index provided.
     *
     * @param index The block index
     * @return The information about the block
     */
    public Map<String,Object> getBlock(Integer index) throws JsonRPCException, IOException {
        Sha256Hash blockHash = getBlockHash(index);
        return getBlock(blockHash);
    }

    /**
     *
     * @param generate        turn generation on or off
     * @param genproclimit    Generation is limited to [genproclimit] processors, -1 is unlimited
     *                        in regtest mode genproclimit is number of blocks to generate immediately
     * @return Object         Bitcoin 0.10.0+: An array containing the block header hashes of the generated blocks or null
     *                        if no blocks were generated
     *                        Bitcoin 0.9.x: null
     *
     * @throws IOException
     */
    public Object setGenerate(Boolean generate, Long genproclimit) throws JsonRPCException, IOException {
        List<Object> params = createParamList(generate, genproclimit);
        Object result = send("setgenerate", params);
        return result;
    }

    public Object generateBlock() throws JsonRPCException, IOException {
        return generateBlocks(1L);
    }

    public Object generateBlocks(Long blocks) throws JsonRPCException, IOException {
        return setGenerate(true, blocks);
    }

    public Address getNewAddress() throws JsonRPCException, IOException {
        return getNewAddress(null);
    }

    public Address getNewAddress(String account) throws JsonRPCException, IOException {
        List<Object> params = createParamList(account);
        String addr = send("getnewaddress", null);
        Address address = null;
        try {
            // TODO: Is it safe to use null for params here?
            address = new Address(null, addr);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
        return address;
    }

    public Address getAccountAddress(String account) throws JsonRPCException, IOException {
        List<Object> params = createParamList(account);
        String addr = (String) send("getaccountaddress", params);
        Address address = null;
        try {
            address = new Address(null, addr);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
        return address;
    }

    /**
     * Return a private key from the server
     * (must be in wallet mode with unlocked or unencrypted wallet)
     * @param address Address corresponding to private key to return
     * @return the private key
     * @throws IOException
     * @throws JsonRPCStatusException
     */
    public ECKey dumpPrivKey(Address address) throws IOException, JsonRPCStatusException {
        List<Object> params = createParamList(address.toString());
        String base58Key = send("dumpprivkey", params);
        ECKey key;
        try {
            DumpedPrivateKey dumped = new DumpedPrivateKey(null, base58Key);
            key = dumped.getKey();
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);  // Should never happen
        }
        return key;
    }

    public Boolean moveFunds(Address fromaccount, Address toaccount, BigDecimal amount, Integer minconf, String comment) throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromaccount, toaccount, amount, minconf, comment);
        Boolean result = (Boolean) send("move", params);
        return result;
    }

    /**
     * Creates a raw transaction spending the given inputs to the given destinations.
     *
     * Note: the transaction inputs are not signed, and the transaction is not stored in the wallet or transmitted to
     * the network.
     *
     * @param inputs  The outpoints to spent
     * @param outputs The destinations and amounts to transfer
     * @return The hex-encoded raw transaction
     * @throws JsonRPCException
     * @throws IOException
     */
    public String createRawTransaction(List<Outpoint> inputs, Map<Address, BigDecimal> outputs)
            throws JsonRPCException, IOException {
        // Convert inputs from typed list to list-of-maps for conversion to JSON
        List<Map<String, Object>> inputsJson = new ArrayList<Map<String, Object>>();
        for (Outpoint outpoint : inputs) {
            Map<String, Object> outMap = new HashMap<String, Object>();
            outMap.put("txid", outpoint.getTxid().toString());
            outMap.put("vout", outpoint.getVout());
            inputsJson.add(outMap);
        }
        List<Object> params = Arrays.asList(inputsJson, outputs);
        String transactionHex = send("createrawtransaction", params);
        return transactionHex;
    }

    /**
     * Signs inputs of a raw transaction.
     *
     * @param unsignedTransaction The hex-encoded raw transaction
     * @return The signed transaction and information whether it has a complete set of signature
     * @throws IOException
     * @throws JsonRPCException
     */
    public Map<String, Object> signRawTransaction(String unsignedTransaction) throws IOException, JsonRPCException {
        List<Object> params = createParamList(unsignedTransaction);
        Map<String, Object> signedTransaction = send("signrawtransaction", params);
        return signedTransaction;
    }

    public Object getRawTransaction(Sha256Hash txid, Boolean verbose) throws JsonRPCException, IOException {
        Object result;
        if (verbose) {
            result = getRawTransactionMap(txid);    // Verbose means JSON
        } else {
            result = getRawTransactionBytes(txid);  // Not-verbose is Binary
        }
        return result;
    }

    /* Return a BitcoinJ Transaction type */
    public Transaction getRawTransaction(Sha256Hash txid) throws JsonRPCException, IOException {
        byte[] raw = getRawTransactionBytes(txid);
        // Hard-code RegTest for now
        // TODO: All RPC client connections should have a BitcoinJ params object?
        Transaction tx = new Transaction(RegTestParams.get(), raw);
        return tx;
    }

    public byte[] getRawTransactionBytes(Sha256Hash txid) throws JsonRPCException, IOException {
        List<Object> params = createParamList(txid.toString());
        String hexEncoded = send("getrawtransaction", params);
        byte[] raw = BitcoinClient.hexStringToByteArray(hexEncoded);
        return raw;
    }

    /* TODO: Return a stronger type than an a Map? */
    public Map<String, Object> getRawTransactionMap(Sha256Hash txid) throws JsonRPCException, IOException {
        List<Object> params = createParamList(txid.toString(), 1);
        Map<String, Object> json = send("getrawtransaction", params);
        return json;
    }

    public Sha256Hash sendRawTransaction(Transaction tx) throws JsonRPCException, IOException {
        return sendRawTransaction(tx, null);
    }

    public Sha256Hash sendRawTransaction(String hexTx) throws JsonRPCException, IOException {
        return sendRawTransaction(hexTx, null);
    }

    public Sha256Hash sendRawTransaction(Transaction tx, Boolean allowHighFees) throws JsonRPCException, IOException {
        String hexTx = transactionToHex(tx);
        return sendRawTransaction(hexTx, allowHighFees);
    }

    public Sha256Hash sendRawTransaction(String hexTx, Boolean allowHighFees) throws JsonRPCException, IOException {
        List<Object> params = createParamList(hexTx, allowHighFees);
        String txid = send("sendrawtransaction", params);
        Sha256Hash hash = new Sha256Hash(txid);
        return hash;
    }

    public BigDecimal getReceivedByAddress(Address address) throws JsonRPCException, IOException {
        return getReceivedByAddress(address, 1);   // Default to 1 or more confirmations
    }

    public BigDecimal getReceivedByAddress(Address address, Integer minConf) throws JsonRPCException, IOException {
        List<Object> params = createParamList(address.toString(), minConf);
        BigDecimal balance = BigDecimal.valueOf((Double) send("getreceivedbyaddress", params));
        return balance;
    }

    public List<Object> listReceivedByAddress(Integer minConf, Boolean includeEmpty ) throws JsonRPCException, IOException {
        List<Object> params = createParamList(minConf, includeEmpty);
        List<Object> addresses = send("listreceivedbyaddress", params);
        return addresses;
    }

    /**
     * Returns a list of unspent transaction outputs with at least one confirmation.
     *
     * @return The unspent transaction outputs
     * @throws JsonRPCException
     * @throws IOException
     */
    public List<UnspentOutput> listUnspent() throws JsonRPCException, IOException {
        return listUnspent(null, null, null);
    }

    /**
     * Returns a list of unspent transaction outputs with at least {@code minConf} and not more than {@code maxConf}
     * confirmations.
     *
     * @param minConf The minimum confirmations to filter
     * @param maxConf The maximum confirmations to filter
     * @return The unspent transaction outputs
     * @throws JsonRPCException
     * @throws IOException
     */
    public List<UnspentOutput> listUnspent(Integer minConf, Integer maxConf)
            throws JsonRPCException, IOException {
        return listUnspent(minConf, maxConf, null);
    }

    /**
     * Returns a list of unspent transaction outputs with at least {@code minConf} and not more than {@code maxConf}
     * confirmations, filtered by a list of addresses.
     *
     * @param minConf The minimum confirmations to filter
     * @param maxConf The maximum confirmations to filter
     * @param filter  Include only transaction outputs to the specified addresses
     * @return The unspent transaction outputs
     * @throws JsonRPCException
     * @throws IOException
     */
    public List<UnspentOutput> listUnspent(Integer minConf, Integer maxConf, Iterable<Address> filter)
            throws JsonRPCException, IOException {
        List<String> addressFilter = null;
        if (filter != null) {
            addressFilter = applyToString(filter);
        }

        List<Object> params = createParamList(minConf, maxConf, addressFilter);
        List<Map<String, Object>> unspentMaps = send("listunspent", params);
        List<UnspentOutput> unspent = new ArrayList<UnspentOutput>();
        for (Map<String, Object> uoMap : unspentMaps) {
            String txstr = (String) uoMap.get("txid");
            Sha256Hash txid = new Sha256Hash(txstr);
            int vout = (Integer) uoMap.get("vout");
            String addrStr = (String) uoMap.get("address");
            Address addr = null;
            try {
                addr = new Address(null, addrStr);
            } catch (AddressFormatException e) {
                e.printStackTrace();
            }
            String account = (String) uoMap.get("account");
            String scriptPubKey = (String) uoMap.get("scriptPubKey");
            Double amountDb = (Double) uoMap.get("amount");
            BigDecimal amount = BigDecimal.valueOf(amountDb);
            int confirmations = (Integer) uoMap.get("confirmations");
            UnspentOutput uo = new UnspentOutput(txid,vout,addr,account,scriptPubKey,amount,confirmations);
            unspent.add(uo);
        }
        return unspent;
    }

    /**
     * Returns details about an unspent transaction output.
     *
     * @param txid The transaction hash
     * @param vout The transaction output index
     * @return Details about an unspent output or nothing, if the output was already spent
     */
    public Map<String,Object> getTxOut(Sha256Hash txid, Integer vout) throws JsonRPCException, IOException {
        return getTxOut(txid, vout, null);
    }

    /**
     * Returns details about an unspent transaction output.
     *
     * @param txid The transaction hash
     * @param vout The transaction output index
     * @param includeMemoryPool Whether to included the memory pool
     * @return Details about an unspent output or nothing, if the output was already spent
     */
    public Map<String,Object> getTxOut(Sha256Hash txid, Integer vout, Boolean includeMemoryPool)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(txid.toString(), vout, includeMemoryPool);
        Map<String, Object> json = send("gettxout", params);
        return json;
    }

    public BigDecimal getBalance() throws JsonRPCException, IOException {
        return getBalance(null, null);
    }

    public BigDecimal getBalance(String account) throws JsonRPCException, IOException {
        return getBalance(account, null);
    }

    public BigDecimal getBalance(String account, Integer minConf) throws JsonRPCException, IOException {
        List<Object> params = createParamList(account, minConf);
        Double balanceBTCd = send("getbalance", params);
        // Beware of the new BigDecimal(double d) constructor, it results in unexpected/undesired values.
        BigDecimal balanceBTC = BigDecimal.valueOf(balanceBTCd);
        return balanceBTC;
    }

    public Sha256Hash sendToAddress(Address address, BigDecimal amount) throws JsonRPCException, IOException {
        return sendToAddress(address, amount, null, null);
    }

    public Sha256Hash sendToAddress(Address address, BigDecimal amount, String comment, String commentTo) throws JsonRPCException, IOException {
        List<Object> params = createParamList(address.toString(), amount, comment, commentTo);
        String txid = send("sendtoaddress", params);
        Sha256Hash hash = new Sha256Hash(txid);
        return hash;
    }

    public Sha256Hash sendFrom(String account, Address address, BigDecimal amount) throws JsonRPCException, IOException {
        List<Object> params = createParamList(account, address.toString(), amount);
        String txid = send("sendfrom", params);
        Sha256Hash hash = new Sha256Hash(txid);
        return hash;
    }

    public Sha256Hash sendMany(String account, Map<Address, BigDecimal> amounts) throws JsonRPCException, IOException {
        List<Object> params = Arrays.asList(account, amounts);
        String txid = send("sendmany", params);
        Sha256Hash hash = new Sha256Hash(txid);
        return hash;
    }

    /**
     * Set the transaction fee per kB.
     *
     * @param amount The transaction fee in BTC/kB rounded to the nearest 0.00000001.
     * @return True if successful
     */
    public Boolean setTxFee(BigDecimal amount) throws JsonRPCException, IOException {
        List<Object> params = createParamList(amount);
        Boolean result = send("settxfee", params);
        return result;
    }

    public Map<String, Object> getTransaction(Sha256Hash txid) throws JsonRPCException, IOException {
        List<Object> params = createParamList(txid.toString());
        Map<String, Object> transaction = send("gettransaction", params);
        return transaction;
    }

    public Map<String, Object> getInfo() throws JsonRPCException, IOException {
        Map<String, Object> result = send("getinfo", null);
        return result;
    }

    /**
     * Returns a list of available commands.
     *
     * Commands which are unavailable will not be listed, such as wallet RPCs, if wallet support is disabled.
     *
     * @return The list of commands
     */
    public List<String> getCommands() throws JsonRPCException, IOException {
        List<String> commands = new ArrayList<String>();
        for (String entry : help().split("\n")) {
            if (!entry.isEmpty() && !entry.matches("== (.+) ==")) {
                String command = entry.split(" ")[0];
                commands.add(command);
            }
        }
        return commands;
    }

    /**
     * Returns a human readable list of available commands.
     *
     * Bitcoin Core 0.9 returns an alphabetical list of commands, and Bitcoin Core 0.10 returns a categorized list of
     * commands.
     *
     * @return The list of commands as string
     */
    public String help() throws JsonRPCException, IOException {
        return help(null);
    }

    /**
     * Returns helpful information for a specific command.
     *
     * @param command The name of the command to get help for
     * @return The help text
     */
    public String help(String command) throws JsonRPCException, IOException {
        List<Object> params = createParamList(command);
        String result = send("help", params);
        return result;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private String transactionToHex(Transaction tx) {
        // From: http://bitcoin.stackexchange.com/questions/8475/how-to-get-hex-string-from-transaction-in-bitcoinj
        final StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb);
        byte[] bytes = tx.bitcoinSerialize();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        formatter.close();
        return sb.toString();
    }

    /**
     * Applies toString() to every element of {@code elements} and returns a list of the results.
     *
     * @param elements The elements
     * @return The list of strings
     */
    private <T> List<String> applyToString(Iterable<T> elements) {
        List<String> stringList = new ArrayList<String>();
        for (T element : elements) {
            String elementAsString = element.toString();
            stringList.add(elementAsString);
        }
        return stringList;
    }

}
