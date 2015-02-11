modules = {
    timeseries {
        dependsOn 'bootstrap, jquery'
        resource url:'/css/timeseries.css', attrs:[media:'screen, projection, print']
        resource url:'/js/timeseries.js'
    }

}