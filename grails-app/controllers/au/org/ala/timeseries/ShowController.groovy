package au.org.ala.timeseries

import grails.converters.JSON
import groovy.json.JsonSlurper

class ShowController {

    static Map nameMap = [:]
    static Map polygonYearMap = [:]
    static Map polygonMonthMap = [:]

    /**
     * Load the polygons from the data file, partitioning by
     * species groups
     * species
     * year
     *
     * @return
     */
    private def initPolygonMap(polygonMap, fileName){

        nameMap = [:]

        def file = new File(fileName)
        def groupLookup = getGroupLookup()
        def counter = 0
        def polygonsLoaded = 0
        file.eachLine { line ->

            def parts = line.split("\t")
            def nameParts = parts[0].split("\\|")
            def family = nameParts[0]
            def sciName = nameParts[1]

            if (isSpeciesOrSubspecies(sciName)) {

                def temporalPeriod = parts[1]
                def polygons = parts[2].split("\\|")
                def areas = parts[3].split("\\|")

                log.debug("Loading ${sciName} ${temporalPeriod}")

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
                    polygonsForSciName.put(temporalPeriod, polygons)
                    counter++
                    polygonsLoaded += polygons.size()
                }
            }
        }

        //for name map, lookup taxa
        log.info("Loaded ${polygonMap.keySet().size()} families, ${polygonsLoaded} polygons, ${counter} years")
    }

    /**
     * Load the polygons from the data file, partitioning by
     * species groups
     * species
     * year
     *
     * @return
     */
    private def initPolygonMaps(){
        initPolygonMap(polygonMonthMap, "/data/timeseries/config/months.csv")
        initPolygonMap(polygonYearMap, "/data/timeseries/config/years.csv")
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
        def classification = js.parseText(new URL("${grailsApplication.config.bieService.baseURL}/classification/" + family).getText())
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
        def hierarchy = js.parseText(new URL("${grailsApplication.config.biocacheService.baseURL}/explore/hierarchy").getText())
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
            if (!polygonYearMap) {
                initPolygonMaps()
            }
            nameMap.keySet().each { group ->
                def names = nameMap.get(group)
                ([names: names] as JSON).toString()
                def resp = postJsonElements("${grailsApplication.config.bieService.baseURL}/species/lookup/bulk", ([names: names] as JSON).toString())

                def content = []
                //trim it down to required
                resp.each {
                    content << [scientificName: it.name, commonName: it.commonNameSingle, image: it.smallImageUrl, guid: it.guid]
                }
                //sort the names by common name then sci name where common name not available
                namesForDisplay.put(group, content.sort { taxon1, taxon2 ->
                    if(!taxon1.commonName && taxon2.commonName){
                        1
                    } else if(taxon1.commonName && !taxon2.commonName){
                        -1
                    } else if(taxon1.commonName && taxon2.commonName) {
                        taxon1.commonName.compareToIgnoreCase(taxon2.commonName)
                    } else {
                        taxon1.scientificName.compareToIgnoreCase(taxon2.scientificName)
                    }
                })
            }
        }
        render namesForDisplay as JSON
    }

    def getByNameYear(){
        if(!polygonYearMap){
            initPolygonMaps()
        }
        def polygons = polygonYearMap.get(params.sciName)
        render polygons as JSON
    }

    def getByNameMonth(){
        if(!polygonMonthMap){
            initPolygonMaps()
        }
        def polygons = polygonMonthMap.get(params.sciName)
        def months = polygons.keySet().sort { it.toInteger() }
        def sortedPolygons = [:]
        months.each { month ->
            sortedPolygons.put(monthsLookup[month.toInteger()-1], polygons.get(month))
        }
        render sortedPolygons as JSON
    }

    def monthsLookup = [
            "January","February","March","April","May","June","July","August","September","October", "November", "December"
    ]

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
            if (!resp && conn.getResponseCode() == 201) {
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
