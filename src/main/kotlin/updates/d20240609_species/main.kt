package net.nprod.nap.updater

import org.apache.jena.query.*
import org.apache.jena.sparql.core.Transactional
import org.apache.jena.tdb2.TDB2Factory
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import org.apache.jena.vocabulary.RDF

enum class TaxonomicLevel {
    CLASS,
    FAMILY,
    GENUS,
    SPECIES,
    SUBSPECIES
}

data class Organism(
    val className: String,
    val family: String,
    val genus: String,
    val species: String,
    val subspecies: String,
)

data class Taxa(
    val parentTaxa: MutableSet<Taxa> = mutableSetOf(),
    val taxonomicLevel: TaxonomicLevel,
    val name: String,
    var newId: Int = 0
)

class TaxaSet : MutableSet<Taxa> by mutableSetOf() {
    val taxaMap = mutableMapOf<Pair<TaxonomicLevel, String>, Taxa>()

    // This is gross, but as computers are fast who cares?
    fun addOrGet(taxonomicLevel: TaxonomicLevel, name: String): Taxa {
        val pair = Pair(taxonomicLevel, name)
        return taxaMap[pair] ?: Taxa(taxonomicLevel = taxonomicLevel, name = name).also {
            taxaMap[pair] = it
        }
    }
}


object D20240609_species {
    // Gather all the species, genus, families
    // group them
    // create a new :
    //  a n:taxon ; n:name name; n:taxonomic_level .... ; n:parent_taxon
    // associate the taxon to the organism
    @JvmStatic
    fun main(args: Array<String>) {
        val dataset = TDB2Factory.connectDataset("data/tdb_nap_raw")
        val organismMap = mutableMapOf<Organism, MutableSet<String>>()


        //
        // DO NOT UNDO ONCE IT IS PUBLIC OR YOU WILL MESS UP THE IDs
        //

        dataset.executeWrite {
            // A sparql query that would undo all of this
            val undoQueries = listOf("""
                PREFIX n: <https://nap.nprod.net/>
                DELETE {
                    ?update a n:update.
                    ?update n:name "20240609_species".
                }
                WHERE {
                    ?update a n:update.
                    ?update n:name "20240609_species".
                }
            """.trimIndent(), """
                PREFIX n: <https://nap.nprod.net/>
                DELETE {
                    ?taxon ?p ?o.
                    ?organism n:taxon ?taxon.
                }
                WHERE {
                    ?taxon a n:taxon.
                    ?taxon ?p ?o.
                    OPTIONAL { ?organism n:taxon ?taxon. }
                }
            """.trimIndent(), """
                PREFIX n: <https://nap.nprod.net/>
                DELETE {
                    ?s n:has_taxon ?o.
                }
                WHERE {
                    ?s n:has_taxon ?o.
                }
            """.trimIndent())

            undoQueries.forEach { undoQuery ->
                val arqQuery = UpdateFactory.create(undoQuery, Syntax.syntaxARQ)
                val qExec = UpdateExecutionFactory.create(arqQuery, dataset)
                qExec.execute()
            }
        }

        dataset.executeRead<Transactional> {
            val updateCheckQuery = """
                PREFIX n: <https://nap.nprod.net/>
                SELECT ?update {
                    ?update a n:update.
                    ?update n:name "20240609_species".
                }
            """.trimIndent()

            val arqUpdateCheckQuery = QueryFactory.create(updateCheckQuery, Syntax.syntaxARQ)
            val qExecUpdateCheck = QueryExecutionFactory.create(arqUpdateCheckQuery, dataset)
            if (qExecUpdateCheck.execSelect().hasNext()) {
                println("This update has been run already, and it would make an horrible mess if you run it again as ids may not be repeatable")
                return@executeRead
            }


            val query = """
                PREFIX n: <https://nap.nprod.net/>
                SELECT ?organism ?class ?family ?genus ?species ?subspecies {
                 ?organism a n:organism;
                         n:organismclass/n:name ?class;
                           n:familyname ?family;
                            n:genusname ?genus;
                            n:speciesname ?species;
                            n:subspeciesname ?subspecies.
                }
            """.trimIndent()
            val arqQuery = QueryFactory.create(query, Syntax.syntaxARQ)
            val qExec = QueryExecutionFactory.create(arqQuery, dataset)
            qExec.execSelect().let {
                while (it.hasNext()) {
                    val row = it.next()
                    val organism = row.getResource("organism")
                    val className = row.getLiteral("class").string
                    val family = row.getLiteral("family").string
                    val genus = row.getLiteral("genus").string
                    val species = row.getLiteral("species").string
                    val subspecies = row.getLiteral("subspecies").string
                    val organismDC = Organism(className, family, genus, species, subspecies)
                    if (organismMap[organismDC] == null) {
                        organismMap[organismDC] = mutableSetOf()
                    }
                    organismMap[organismDC]!!.add(organism.uri) // It has a default
                }
            }
        }

        // Now that we have the map, we can generate all the new taxa
        // We could have done that cleaner but that will do
        val taxaSet = TaxaSet()
        val taxaToOriginalOrganisms = mutableMapOf<Taxa, MutableSet<String>>()

        organismMap.keys.toList().forEach { organism ->
            val classTaxa = if (organism.className != "") {
                taxaSet.addOrGet(taxonomicLevel = TaxonomicLevel.CLASS, name = organism.className).also {
                    taxaSet.add(it)
                }
            } else {
                null
            }


            val familyTaxa = if (organism.family != "") {
                taxaSet.addOrGet(taxonomicLevel = TaxonomicLevel.FAMILY, name = organism.family).also { taxa ->
                    classTaxa?.let { taxa.parentTaxa.add(it) }
                    taxaSet.add(taxa)
                }
            } else {
                null
            }


            val genusTaxa = if (organism.genus != "") {
                taxaSet.addOrGet(taxonomicLevel = TaxonomicLevel.GENUS, name = organism.genus).also { taxa ->
                    familyTaxa?.let { taxa.parentTaxa.add(it) }
                    taxaSet.add(taxa)
                }
            } else {
                null
            }

            val speciesTaxa = if (organism.species != "") {
                taxaSet.addOrGet(taxonomicLevel = TaxonomicLevel.SPECIES, name = organism.species).also { taxa ->
                    genusTaxa?.let { taxa.parentTaxa.add(it) }
                    taxaSet.add(taxa)
                }
            } else {
                null
            }

            val subspeciesTaxa = if (organism.subspecies != "") {
                taxaSet.addOrGet(taxonomicLevel = TaxonomicLevel.SUBSPECIES, name = organism.subspecies).also { taxa ->
                    speciesTaxa?.let { taxa.parentTaxa.add(it) }
                    taxaSet.add(taxa)
                }
            } else {
                null
            }


            val taxa = listOfNotNull(familyTaxa, genusTaxa, speciesTaxa, subspeciesTaxa)
            if (taxa.isNotEmpty()) {
                taxaToOriginalOrganisms[taxa.last()] = organismMap[organism]!!
            }
        }

        // Now give them all a number
        var id = 0
        taxaSet.forEach {
            it.newId = id++
        }

        dataset.executeWrite {
            val model = dataset.defaultModel
            val taxon = model.createResource("https://nap.nprod.net/taxon")
            taxaToOriginalOrganisms.forEach { (taxa, organisms) ->
                val resource = model.createResource("https://nap.nprod.net/taxon/${taxa.newId}")
                resource.addProperty(RDF.type, taxon)
                resource.addProperty(model.createProperty("https://nap.nprod.net/name"), model.createLiteral(taxa.name))
                resource.addProperty(RDF.type, model.createResource("https://nap.nprod.net/taxon"))
                resource.addProperty(
                    model.createProperty("https://nap.nprod.net/taxonomic_level"),
                    model.createLiteral(taxa.taxonomicLevel.name)
                )
                taxa.parentTaxa.forEach { parentTaxa ->
                    resource.addProperty(
                        model.createProperty("https://nap.nprod.net/parent_taxon"),
                        model.createResource("https://nap.nprod.net/taxon/${parentTaxa.newId}")
                    )
                }
                // Now for each of the organisms, we need to link them to the taxa
                organisms.forEach { organismUri ->
                    model.createResource(organismUri)
                        .addProperty(model.createProperty("https://nap.nprod.net/has_taxon"), resource)
                }
            }
            val update = model.createResource("https://nap.nprod.net/update/20240609_species")
            update.addProperty(
                model.createProperty("https://nap.nprod.net/name"),
                model.createLiteral("20240609_species")
            )
            model.add(update, RDF.type, model.createProperty("https://nap.nprod.net/update"))
        }

        dataset.close()
    }
}
