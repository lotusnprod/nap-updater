import org.apache.jena.query.ReadWrite
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.tdb2.TDB2Factory

object ImportN3ToTDB {
    @JvmStatic
    fun main(args: Array<String>) {
        val dataset = TDB2Factory.connectDataset("data/tdb2")
        dataset.executeWrite {
            RDFDataMgr.read(dataset, "data/output.n3")
        }

        dataset.begin(ReadWrite.WRITE)
        dataset.close()
    }
}
