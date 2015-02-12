<!DOCTYPE html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta http-equiv="X-UA-Compatible" content="IE=edge">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<meta name="description" content="">
	<meta name="layout" content="${grailsApplication.config.skin.layout?:'main'}"/>
	<meta name="author" content="">
	<title>Bird distributions | Atlas of Living Australia</title>
	<!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
	<!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
	<!--[if lt IE 9]>
        <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
        <script src="https://oss.maxcdn.com/libs/respond.js/1.4.2/respond.min.js"></script>
    <![endif]-->
	<link href="http://leafletjs.com/dist/leaflet.css" rel="stylesheet"/>
	<script src="http://leafletjs.com/dist/leaflet.js"></script>
	<script src='//api.tiles.mapbox.com/mapbox.js/plugins/leaflet-omnivore/v0.2.0/leaflet-omnivore.min.js'></script>
	<r:require modules="timeseries"/>
</head>

<body>

<div class="row" style="height:700px;">

	<!-- Sidebar -->
	<div id="sidebar-wrapper" class="col-md-3 hidden-sm hidden-xs">
		<h2 class="heading-large" id="main-heading">Bird time series</h2>
	</div>
	<!-- /#sidebar-wrapper -->

	<!-- Page Content -->
	<div id="page-content-wrapperXXX" class="col-md-9">

		<div class="visible-sm visible-xs" style="margin-top: 30px; margin-left: 10px; margin-bottom:20px;">
			<label>Select bird: </label>
			<select id="taxon-select" class="form-control">
			</select>
		</div>

		<div>
			<div id="map" style="width:100%; height:700px;"> </div>
			<div id="taxonInfo" class="hide">
				<a class="speciesPageLink" href="">
				<h2 class="commonName"></h2>
				<h3 class="scientificName"></h3>
				<img src=""/>
				</a>
				<br/>
				<div id="yearTicker">
					<span id="currentYear"></span>
				</div>

				<div id="startStopButtons" style="margin-top:10px;">
					<a href="#" class="btn btn-default start">Start</a>
					<a href="#" class="btn btn-default stop">Stop</a>
					<p style="padding-top:10px;">
						<select id="temporalPeriod" class="form-control">
							<option value="1">By month</option>
							<option value="0">By decade</option>
						</select>
					</p>
					<p style="padding-top:5px;">
						<select id="timeInterval" class="form-control">
							<option value="1000">1 second interval</option>
							<option value="2000">2 seconds  interval</option>
							<option value="5000">5 second  interval</option>
							<option value="10000">10 second interval</option>
						</select>
					</p>
				</div>
			</div>
		</div>

		<div id="getStarted">
			<h2>Bird distributions</h2>
			<p>
				This is a simple tool for exploring bird distributions through the decades.
				These distributions have been derived from occurrence data accessible in the Atlas.
				<br/>
				To use this tool, select a bird group e.g. Doves, and then a bird from the left hand side menu,
				or click one of the examples below:
				<ul>
					<li><a href="javascript:loadByName('Laughing Kookaburra');">Laughing Kookaburra</a></li>
					<li><a href="javascript:loadByName('Gang-gang Cockatoo');">Gang-gang Cockatoo</a></li>
					<li><a href="javascript:loadByName('Calyptorhynchus (Zanda) latirostris');">Carnaby's Black-cockatoo</a></li>
				</ul>
			</p>
		</div>
	</div>
	<!-- /#page-content-wrapper -->
</div>

<r:script>

	var POLY_TRANS_YEAR = 0;
	var POLY_TRANS_MONTH = 1;

	var POLY_TRANS = {
		map: L.map('map').setView([-26.1, 133.9], 4),
		polygonsMap: {},
		loadedTemporalPeriods: [],
		polygonsLoaded: false,
		currentLoadedPolygons: [],
		polygonTransitionsRunning: false,
		currentTemporalIdx: 0,
		temporalType: POLY_TRANS_MONTH,
		taxon: "",
		taxonIdx: 0,
		taxa: [],
		timer: null,
		timeInterval: 1000
	}

	L.tileLayer('https://{s}.tiles.mapbox.com/v3/{id}/{z}/{x}/{y}.png', {
		maxZoom: 18,
		attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, ' +
		'<a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, ' +
		'Imagery © <a href="http://mapbox.com">Mapbox</a>',
		id: 'examples.map-i875mjb7'
	}).addTo(POLY_TRANS.map);

	var popup = L.popup();

	var taxa = [];

	function loadNames() {
		$.get("show/names", function (data) {
			$.each(data, function (groupName, group) {

				var $group = $('<ul class="species-list">');
				var $optGroup = $('<optgroup label="' + groupName + '">');
				$('#taxon-select').append($optGroup);

				$.each(group, function (idx, taxon) {

					var idx = POLY_TRANS.taxa.push(taxon) - 1;

					var nameToDisplay = "";
					if (taxon.commonName) {
						nameToDisplay = "<span class='commonName mainName'>" + taxon.commonName + "</span><br/><span class='secondName scientificName'>" + taxon.scientificName + "</span>"
					} else {
						nameToDisplay = "<span class='scientificName mainName'>" + taxon.scientificName + "</span>"
					}

					$group.append('<li><a href="javascript:loadTaxon(' + idx + ');">' + nameToDisplay + '</a></li>');

					var selectDisplayName = taxon.scientificName;
					if (taxon.commonName != null) {
						selectDisplayName = taxon.commonName + " - " + taxon.scientificName;
					}

					$optGroup.append('<option value="' + idx + '">' + selectDisplayName + '</option>');
				});

				var $spGroup = $('<div class="speciesGroup">')

				$spGroup.append('<h4><a class="groupSelector" href="#">' + groupName + '</a></h4>');
				$spGroup.append($group);
				$('#sidebar-wrapper').append($spGroup);
				$group.hide();
			});

			$(".groupSelector").on("click", function () {
				$(this).parents('div.speciesGroup').find(".species-list").toggle('slow');
				console.log('displayed ???');
			});
		});
	}

	function loadTaxon(taxonIdx) {

		$('#getStarted').addClass('hide');

		stopTransitions();
		POLY_TRANS.taxonIdx = taxonIdx
		POLY_TRANS.taxon = POLY_TRANS.taxa[taxonIdx];
		console.log("loading : " + POLY_TRANS.taxon.scientificName);
		POLY_TRANS.polygonTransitionsRunning = false;

		if (POLY_TRANS.currentLoadedPolygons) {
			$.each(POLY_TRANS.currentLoadedPolygons, function (idx, polygonToRemove) {
				POLY_TRANS.map.removeLayer(polygonToRemove);
			});
		}

		POLY_TRANS.polygonsMap = {};
		POLY_TRANS.loadedTemporalPeriods = [];
		POLY_TRANS.polygonsLoaded = false;
		POLY_TRANS.currentLoadedPolygons = [];
		POLY_TRANS.currentTemporalIdx = 0;
		POLY_TRANS.polygonTransitionsRunning = true;

		var methodUrl = "getByNameYear";
		if(POLY_TRANS.temporalType == POLY_TRANS_MONTH){
			methodUrl = "getByNameMonth";
		}

		console.log("show/" + methodUrl + "?sciName=" + encodeURIComponent(POLY_TRANS.taxon.scientificName));

		$.get("show/" + methodUrl + "?sciName=" + encodeURIComponent(POLY_TRANS.taxon.scientificName), function (data) {
			$.each(data, function (period, polygons) {
				POLY_TRANS.polygonsMap[period] = [];
				POLY_TRANS.loadedTemporalPeriods.push(period);
				$.each(polygons, function (idx, polygon) {
					POLY_TRANS.polygonsMap[period].push(omnivore.wkt.parse(polygon));
				});
				POLY_TRANS.polygonsLoaded = true
			})

			$('#taxonInfo').removeClass('hide')
			$('#taxonInfo').find('.scientificName').html(POLY_TRANS.taxon.scientificName);
			$('#taxonInfo').find('.commonName').html(POLY_TRANS.taxon.commonName);
			$('#taxonInfo').find('img').attr('src', POLY_TRANS.taxon.image);
			$('#taxonInfo').find('.speciesPageLink').attr('href', "http://bie.ala.org.au/species/" + POLY_TRANS.taxon.guid);

			startTransitions();
		});
	}

	function loadNextPeriod() {

		if (POLY_TRANS.currentTemporalIdx + 1 < POLY_TRANS.loadedTemporalPeriods.length) {
			POLY_TRANS.currentTemporalIdx++;
		} else {
			POLY_TRANS.currentTemporalIdx = 0;
		}

		var theTemporalPeriod= POLY_TRANS.loadedTemporalPeriods[POLY_TRANS.currentTemporalIdx];

		var temporalDisplay = "";

		if (POLY_TRANS.temporalType == POLY_TRANS_MONTH) {
			temporalDisplay = theTemporalPeriod;
		} else {
			var range = parseInt(theTemporalPeriod) + 9;
			var currentYear = new Date().getFullYear()
			if (range > currentYear) {
				range = currentYear;
			}
			temporalDisplay = theTemporalPeriod + " - " + range
		}

		$('#currentYear').html(temporalDisplay);

		//remove from map
		$.each(POLY_TRANS.currentLoadedPolygons, function (idx, polygonToRemove) {
			POLY_TRANS.map.removeLayer(polygonToRemove);
		});

		POLY_TRANS.currentLoadedPolygons = [];

		//add new
		//console.log('Polygons to load: ' + POLY_TRANS.polygonsMap[theYear].length);
		$.each(POLY_TRANS.polygonsMap[theTemporalPeriod], function (idx, polygon) {
			polygon.addTo(POLY_TRANS.map);
			POLY_TRANS.currentLoadedPolygons.push(polygon);
		});
	}

	function startTransitions() {
		if (POLY_TRANS.timer !== null) return;
		POLY_TRANS.timer = setInterval(function () {
			loadNextPeriod();
		}, POLY_TRANS.timeInterval);
	}

	function stopTransitions() {
		if (POLY_TRANS.timer !== null) {
			clearInterval(POLY_TRANS.timer);
			POLY_TRANS.timer = null
		}
	}

	$(".start").click(function () {
		startTransitions();
	});

	$(".stop").click(function () {
		stopTransitions();
	});

	$('#taxon-select').change(function () {
		var idx = $('#taxon-select').val();
		loadTaxon(idx);
	});

	$('#temporalPeriod').change(function () {
		stopTransitions();
		var temporalPeriod = parseInt($('#temporalPeriod').val());
		POLY_TRANS.currentTemporalIdx = 0;
		POLY_TRANS.temporalType = temporalPeriod;
		loadTaxon(POLY_TRANS.taxonIdx);
	});

	$('#timeInterval').change(function () {
		stopTransitions();
		POLY_TRANS.timeInterval = parseInt($('#timeInterval').val());
		startTransitions();
	});

	//initialise
	loadNames();

	function loadByName(name) {
		$.each(POLY_TRANS.taxa, function (idx, taxon) {
			if (taxon.commonName == name || taxon.scientificName == name || taxon.guid == name) {
				loadTaxon(idx);
				return;
			}
		});
	}

</r:script>

</body>

</html>
