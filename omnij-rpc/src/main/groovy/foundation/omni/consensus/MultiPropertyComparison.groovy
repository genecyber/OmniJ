package foundation.omni.consensus

import foundation.omni.CurrencyID
import foundation.omni.Ecosystem
import foundation.omni.rpc.SmartPropertyListInfo

/**
 *
 */
class MultiPropertyComparison {
    ConsensusFetcher f1
    ConsensusFetcher f2
    boolean compareReserved

    MultiPropertyComparison(ConsensusFetcher left, ConsensusFetcher right, boolean compareReserved = true) {
        this.f1 = left
        this.f2 = right
        this.compareReserved = compareReserved
    }

    def compareAllProperties() {
        List<SmartPropertyListInfo> props = f1.listProperties()
        List<CurrencyID> ids = props.collect({it.id})
        compareProperties(ids)
    }

    def compareProperties(List<CurrencyID> propertiesToCompare) {
        propertiesToCompare.each { id ->
            println "fetching ${id} from fetcher 1"
            ConsensusSnapshot ss1 = f1.getConsensusSnapshot(id)
            println "fetching ${id} from fetcher 2"
            ConsensusSnapshot ss2 = f2.getConsensusSnapshot(id)
            println "comparing ${id} (${ss1.blockHeight}/${ss2.blockHeight})"
            ConsensusComparison comparison = new ConsensusComparison(ss1, ss2)
            for (pair in comparison) {
                if (pair.entry1 != pair.entry2) {
                        println "${pair.address} ${id}: ${pair.entry1} != ${pair.entry2}"
                }
            }
        }
    }
}
