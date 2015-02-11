package au.org.ala.timeseries

import grails.converters.JSON
import groovy.json.JsonSlurper

class ShowController {

    static Map nameMap = [:]
    static Map polygonMap = [:]

    /**
     * Load the polygons from the data file, partitioning by
     * species groups
     * species
     * year
     *
     * @return
     */
    private def initPolygonMap(){

        def file = new File("/data/timeseries/config/polygons.csv")
        def groupLookup = getGroupLookup()
        def counter = 0
        def polygonsLoaded = 0
        file.eachLine { line ->

            def parts = line.split("\t")
            def nameParts = parts[0].split("\\|")
            def family = nameParts[0]
            def sciName = nameParts[1]

            if (isSpeciesOrSubspecies(sciName)) {

                def year = parts[1]
                def polygons = parts[2].split("\\|")
                def areas = parts[3].split("\\|")

                log.debug("Loading ${sciName} ${year}")

                def groupName = getSpeciesGroup(groupLookup, family)

                if(groupName){
                    def group = nameMap.get(groupName)
                    if(!group){
                        group = []
                        nameMap.put(groupName, group)
                    }
                    if(!group.contains(sciName)) {
                        group.add(sciName)
                    }

                    def polygonsForSciName = polygonMap.get(sciName)
                    if (!polygonsForSciName) {
                        polygonsForSciName = [:]
                        polygonMap.put(sciName, polygonsForSciName)
                    }
                    polygonsForSciName.put(year, polygons)
                    counter++
                    polygonsLoaded += polygons.size()
                }
            }
        }

        //for name map, lookup taxa
        log.info("Loaded ${polygonMap.keySet().size()} families, ${polygonsLoaded} polygons, ${counter} years")
    }

    def static groupCache = [:]

    def isSpeciesOrSubspecies(sciName){
        sciName.trim().split(" ").length > 1
    }

    def getSpeciesGroup(groupLookup, family){

        if(groupCache.get(family)){
            return groupCache.get(family)
        }

        def js = new JsonSlurper()
        def classification = js.parseText(new URL("http://bie.ala.org.au/ws/classification/" + family).getText())
        //find the order
        classification.each { node ->
           def lookup = groupLookup.get((node.rank +":" + node.scientificName).toLowerCase())
           if(lookup){
               groupCache.put(family, lookup)
               return lookup
           }
        }
        null
    }

    def getGroupLookup(){
        def js = new JsonSlurper()
        def hierarchy = js.parseText(new URL("http://biocache.ala.org.au/ws/explore/hierarchy").getText())
        def groupLookup = [:]   //   order:MONOTREMATA -> "Monotremes"
        hierarchy.each { speciesGroup ->
            speciesGroup.taxa.each { taxon ->
                groupLookup.put((speciesGroup.taxonRank + ":" + taxon.name).toLowerCase(), taxon.common)
            }
        }
        groupLookup
    }

    def static  namesForDisplay = [:]

    def names(){

        if(!namesForDisplay) {
            if (!polygonMap) {
                initPolygonMap()
            }
            nameMap.keySet().each { group ->
                def names = nameMap.get(group)
                ([names: names] as JSON).toString()
                def resp = postJsonElements("http://bie.ala.org.au/ws/species/lookup/bulk", ([names: names] as JSON).toString())

                def content = []
                //trim it down to required
                resp.each {
                    content << [scientificName: it.name, commonName: it.commonNameSingle, image: it.smallImageUrl, guid: it.guid]
                }

                namesForDisplay.put(group, content)
            }
        }
        render namesForDisplay as JSON
    }

    def getByName(){
        if(!polygonMap){
            initPolygonMap()
        }
        def polygons = polygonMap.get(params.sciName)
        render polygons as JSON
    }

    def postJsonElements(String url, String jsonBody) {
        HttpURLConnection conn = null
        def charEncoding = 'UTF-8'
        try {
            conn = new URL(url).openConnection()
            conn.setDoOutput(true)
            conn.setRequestProperty("Content-Type", "application/json;charset=${charEncoding}");
            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), charEncoding)
            wr.write(jsonBody)
            wr.flush()
            def resp = conn.inputStream.text
            log.debug "fileid = ${conn.getHeaderField("fileId")}"
            //log.debug "resp = ${resp}"
            //log.debug "code = ${conn.getResponseCode()}"
            if (!resp && conn.getResponseCode() == 201) {
                // Field guide code...
                log.debug "field guide catch"
                resp = "{fileId: \"${conn.getHeaderField("fileId")}\" }"
            }
            wr.close()
            return JSON.parse(resp?:"{}")
        } catch (SocketTimeoutException e) {
            def error = "Timed out calling web service. URL= ${url}."
            throw new Exception(error) // exception will result in no caching as opposed to returning null
        } catch (Exception e) {
            def error = "Failed calling web service. ${e.getMessage()} URL= ${url}." +
                    "statusCode: " +conn?.responseCode?:"" +
                    "detail: " + conn?.errorStream?.text
            throw new Exception(error) // exception will result in no caching as opposed to returning null
        }
    }
}
