package net.nprod.nap.server

import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.fuseki.system.FusekiLogging
import org.apache.jena.tdb2.TDB2Factory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

object SPARQLServer {
    private val logger: Logger = LoggerFactory.getLogger(SPARQLServer::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        lateinit var server: FusekiServer
        FusekiLogging.setLogging()

        try {
            logger.info("Starting")
            val dataset = TDB2Factory.connectDataset("data/tdb2")

            server = FusekiServer.create().add("/dataset", dataset)
                .port(3331)
                .build()
            server.start()
            logger.info("Fuseki is running on port 3331")
        } catch (e: IOException) {
            logger.error("Something went wrong", e)
            server.stop()
        }
    }
}
