// Enable/disable various features

var features = {
		heatmap: true,
}

var config = {
	defaultHeatmapIntensity: 100,
}

// Create map layers
var markerLayer = L.featureGroup();
var markerClusterLayer = L.markerClusterGroup();

var gridLayer = L.maidenhead({ worked: [], confirmed: [] });
gridLayer._map = map;

// Set up map
var map = L.map('map', {fullscreenControl: true, layers: markerLayer}).setView([51.505, -0.09], 13);

var maxZoom = 8;

L.tileLayer('https://cartodb-basemaps-{s}.global.ssl.fastly.net/{style}/{z}/{x}/{y}.png', {
    attribution: 'Location data from <a href="http://qrz.com">QRZ.com</a>, stations that have not shared their location will not show<br />Map data &copy; <a href="https://www.openstreetmap.org/">OpenStreetMap</a> contributors, <a href="https://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="https://www.mapbox.com/">Mapbox</a>',
    style: "dark_all",
    maxZoom: 14
}).addTo(map);

// Add layers

var layerControl = new L.Control.Layers(null, {
	'Gridsquares': maidenhead = gridLayer,
	'Clustered Markers': markerClusterLayer,
	'Markers': markerLayer
}).addTo(map);

// Add auto-update custom control
	var autoupdate = true;
	var isProgramaticMove = false;
	var latest_id = 0;
	var autoUpdateControl = L.Control
	.extend({
		options : {
			position : 'topleft'
		//control position - allowed: 'topleft', 'topright', 'bottomleft', 'bottomright'
		},

		onAdd : function(map) {
			var container = L.DomUtil
					.create('div',
							'leaflet-bar leaflet-control leaflet-control-custom auto-update-container');
			container.innerHTML = '<a href="#" title="Automatically update map when new contacts are logged" role="button" aria-label="Automatically pan and zoom to show all contacts"><i id="autoupdateIcon" class="fas fa-search-location" style="padding-top: 5px"></i></a>'
			container.style.fontSize = '20px';
			container.style.backgroundColor = 'white';
			container.style.borderColor = 'red';

			container.onclick = function() {
				toggleAutoUpdate();
			}
			return container;
		},

	});
map.addControl(new autoUpdateControl());

// Disable auto update when user pans/zooms the map (but not on a programatic pan/zoom)
	map.on('zoomstart', function() {
		console.log("isProgramaticMove (zoomStart)", isProgramaticMove);
		if (!isProgramaticMove) {
			disableAutoUpdate();
		}
	});
	
	map.on('dragstart', function() {
		console.log("isProgramaticMove (dragStart)", isProgramaticMove);
		if (!isProgramaticMove) {
			disableAutoUpdate();
		}
	});
	
// Handle the end of a zoom, if the 
	map.on('zoomend', function() {
		console.log("zoom end, autoupdate?", autoupdate);
		if ( autoupdate ) {
			enableAutoUpdate();
		}
	});
	
// Auto-update control functions
	function toggleAutoUpdate() {
		if (autoupdate) {
			disableAutoUpdate();
		} else {
			enableAutoUpdate();
		}
	}

	function enableAutoUpdate() {
		autoupdate = true;
		$('.auto-update-container').css('border-color', 'red');
		console.log("Auto update enabled");

		// Move map to correct position
		isProgramaticMove = true;
		if (markerLayer.getLayers().length > 0) {
			if (map.getBoundsZoom(markerLayer.getBounds().pad(0.2)) > maxZoom) {
				map.setView(markerLayer.getBounds().pad(0.2).getCenter(),
						maxZoom);
			} else {
				map.fitBounds(markerLayer.getBounds().pad(0.2));
			}
		}
		isProgramaticMove = false;
	}

	function disableAutoUpdate() {
		autoupdate = false;
		$('.auto-update-container').css('border-color',
				'rgba(0, 0, 0, 0.2)');
		console.log("Auto update disabled");
	}
	
// Leaflet post-load. This is called when the map is actually displayed to a user. Put stuff in here
// that can't be loaded when the map canvas is not visible (i.e. has zero height). Try to avoid using
// this where possible
var heatmapLayer;
function mapPostLoad() {
	// Heatmap Layer
	if ( features.heatmap && !map.hasLayer(heatmapLayer) ) {
		heatmapLayer = L.heatLayer([], {radius: 25});
		layerControl.addOverlay(heatmapLayer, "Heatmap");
		markerLayer.eachLayer(function (marker){
			heatmapLayer.addLatLng([marker.getLatLng().lat, marker.getLatLng().lng, config.defaultHeatmapIntensity]);
		});
	}
}

// Band to Icon mapping
var bandIcon = {};

// Update map markers
	function update() {
		var jqxhr = $.get("/location" + map_url + "/from/" + latest_id,
				function(data) {
					if (data.length > 0) {
						$.each(data, function(index, value) {
							//console.log("Adding", value);
							
							if ( !(value.band in bandIcon) ) {
								var nextIconIndex = Object.keys(bandIcon).length % icons.length;
								
								bandIcon[value.band] = icons[nextIconIndex];
							}

							var contactDate = moment.utc(value.timestamp).format("YYYY-MM-DD HH:mm");
							
							// Add marker
							var marker = L.marker([ value.lat, value.lon ], {icon: bandIcon[value.band]}).bindPopup(
									"<h1>"	+ value.callsign + "</h1><h4>" + value.name + "</h4><p>Time: "
											+ contactDate + "<br />Band: " + value.band + "</p>");
							markerLayer.addLayer(marker);
							markerClusterLayer.addLayer(marker);

							// Add to grid map
							gridLayer.grids.worked.push(value.grid);
							
							// Note - the heatmap may or may not have been created at this point
							if ( map.hasLayer(heatmapLayer) ) {
								heatmapLayer.addLatLng([value.lat, value.lon, config.defaultHeatmapIntensity]);
							}
							
							latest_id = value.id;
						});
						
						if (autoupdate) {
							isProgramaticMove = true;
							if (map.getBoundsZoom(markerLayer.getBounds().pad(0.2)) > maxZoom) {
								map.setView(markerLayer.getBounds().pad(0.2).getCenter(), maxZoom);
							} else {
								map.fitBounds(markerLayer.getBounds().pad(0.2));
							}
							
							// This gets executed before the zoom ends, so we need to delay it a bit
							setTimeout(function() {isProgramaticMove = false;}, 200);
						}
					}
					

				});
	}

	update();
	setInterval(update, 10000);

	
//populate_map(markerLayer);


// Handle callsign lookup

$(document).ready(function () {
    $("#callsign").keyup(function () {
	  clearTimeout($.data(this, 'timer'));
	  var wait = setTimeout(function() {
		  callsign_lookup($("#callsign").val());
	  }, 500);
	  $(this).data('timer', wait);
    });
    
    $("#liveSwitch").on("click", updateLiveEditMode());
    
    $("#frequency").change(function() {
    	var freq = $("#frequency").val();
    	
    	console.log("New frequency", freq);
    	
    	var band = "N/A";
    	if ( freq >= 135700 && freq <= 137800 ) {
    		band = "2200M";
    	}
    	if ( freq >= 472000 && freq <= 479000 ) {
    		band = "630M";
    	}
    	if ( freq >= 1810000 && freq <= 2000000 ) {
    		band = "160M";
    	}
    	if ( freq >= 3500000 && freq <= 3800000 ) {
    		band = "80M";
    	}
    	if ( freq >= 5258500 && freq <= 5406500 ) {
    		band = "60M";
    	}
    	if ( freq >= 7000000 && freq <= 7200000 ) {
    		band = "40M";
    	}
    	if ( freq >= 10100000 && freq <= 10150000 ) {
    		band = "30M";
    	}
    	if ( freq >= 14000000 && freq <= 14350000 ) {
    		band = "20M";
    	}
    	if ( freq >= 18068000 && freq <= 18168000 ) {
    		band = "17M";
    	}
    	if ( freq >= 21000000 && freq <= 21450000 ) {
    		band = "15M";
    	}
    	if ( freq >= 24890000 && freq <= 24990000 ) {
    		band = "12M";
    	}
    	if ( freq >= 28000000 && freq <= 29700000 ) {
    		band = "10M";
    	}
    	if ( freq >= 50000000 && freq <= 52000000 ) {
    		band = "6M";
    	}
    	if ( freq >= 70000000 && freq <= 70500000 ) {
    		band = "4M";
    	}
    	if ( freq >= 144000000 && freq <= 147000000 ) {
    		band = "2M";
    	}
    	if ( freq >= 430000000 && freq <= 440000000 ) {
    		band = "70CM";
    	}
    	if ( freq >= 1240000000 && freq <= 1325000000 ) {
    		band = "23CM";
    	}
    	if ( freq >= 2310000000 && freq <= 2450000000 ) {
    		band = "13CM";
    	}
    	if ( freq >= 3400000000 && freq <= 3410000000 ) {
    		band = "9CM";
    	}
    	if ( freq >= 5650000000 && freq <= 5850000000 ) {
    		band = "6CM";
    	}
    	if ( freq >= 10000000000 && freq <= 10500000000 ) {
    		band = "3CM";
    	}
    	if ( freq >= 24000000000 && freq <= 24250000000 ) {
    		band = "12MM";
    	}
    	if ( freq >= 47000000000 && freq <= 47200000000 ) {
    		band = "6MM";
    	}
    	if ( freq >= 75500000000 && freq <= 81000000000 ) {
    		band = "4MM";
    	}
    	if ( freq >= 134000000000 && freq <= 136000000000 ) {
    		band = "2MM";
    	}
    	$("#band").val(band);
    });
    
    updateLiveEditMode();
    
    
});

function populate_map(layer) {
	$.ajax({
        type: "GET",
        url: "/location/all",
        cache: false,
        timeout: 600000,
        success: function (data) {
        	$.each(data, function(index, value) {
        		L.marker([value.lat, value.lon]).bindPopup(value.timestamp + "<br /><b>" + value.callsign + "</b><br />" + value.name).addTo(layer);
        	});
        	console.log("AJAX RESULT : ", data);
        },
        error: function (e) {
            console.log("ERROR : ", e);
        },
        complete: function() {
        	$("#callbook-status").html("");
        	console.log(layer);
        	map.fitBounds(layer.getBounds().pad(0.5));
    	}
    });	
}

function callsign_lookup(callsign) {
	if ( callsign.length < 3 ) {
		hide_info_box();
	} else {
		$("#callbook-status").html("Loading...");
		$("#previous-contacts-status").html("Loading...");

		$.ajax({
	        type: "GET",
	        url: "/callbook/" + callsign,
	        cache: false,
	        timeout: 600000,
	        success: function (data) {
	        	
	            console.log("AJAX RESULT : ", data);
	        	
	        	if ( data.length > 0 ) {
	        	
		        	var address = !!data[0].addr1 && data[0].addr1.length > 0 ? data[0].addr1 + "<br />" : "";
		        	address = !!data[0].addr2 && data[0].addr2.length > 0 ? data[0].addr2 + "<br />" : "";
		        	address = !!data[0].county && data[0].county.length > 0 ? data[0].addrcounty + "<br />" : "";
		        	address = !!data[0].state && data[0].state.length > 0 ? data[0].state + "<br />" : "";
		        	address = !!data[0].zip && data[0].zip.length > 0 ? data[0].zip + "<br />" : "";
		        	address = !!data[0].country && data[0].country.length > 0 ? data[0].country : "";
		
		        	$('#callbook-name').html(data[0].fname + " " + data[0].name);
		        	$('#callbook-callsign').html(data[0].callsign);
		        	$('#callbook-aliases').html(data[0].aliases);
		        	$('#callbook-grid').html(data[0].grid);
		        	$('#callbook-address').html(address);
		            $('#callbook-image').attr('src', data[0].image);
		            
		            if ( data[0].qslmgr ) {
		            	$('#callbook-tag-qslmgr').html("QSL via: " + data[0].qslmgr);
		            	$('#callbook-tag-qslmgr').css('visibility', 'visible');
		            } else {
		            	$('#callbook-tag-qslmgr').html("");
		            	$('#callbook-tag-qslmgr').css('visibility', 'hidden');
		            }
		            
		            // Show the info box
		            show_info_box();
		            
		            // Populate input fields
	            	if ( data[0].grid ) {
	            		$('#location').val(data[0].grid);
	            	} else {
	            		$('#location').val(data[0].address);
	            	}
	            	$('#name').val(data[0].fname);
	            	$('#location').val(data[0].grid);
	            	$('#email').val(data[0].email);
		            
		        } else {
	        		// Hide the info box
		        	hide_info_box();
	        	}
	
	        },
	        error: function (e) {
	            console.log("ERROR : ", e);
	        },
	        complete: function() {
	        	$("#callbook-status").html("");
	    	}
	    });
		$.ajax({
	        type: "GET",
	        url: "/lotw-user/" + callsign,
	        cache: false,
	        timeout: 600000,
	        success: function (data) {
	        	if ( data.length > 0 ) {
	        		$("#callbook-tag-lotw").css("visibility", "visible");
	        	} else {
	        		$("#callbook-tag-lotw").css("visibility", "hidden");
	        	}
	            console.log("AJAX RESULT : ", data);
	
	        },
	        error: function (e) {
	            console.log("ERROR : ", e);
	        },
	        complete: function() {
	        	$("#callbook-status").html("");
	    	}
	    });
		$.ajax({
	        type: "GET",
	        url: "/eqsl-user/" + callsign,
	        cache: false,
	        timeout: 600000,
	        success: function (data) {
	        	if ( data.length > 0 ) {
	        		$("#callbook-tag-eqsl").css("visibility", "visible");
	        	} else {
	        		$("#callbook-tag-eqsl").css("visibility", "hidden");
	        	}
	            console.log("AJAX RESULT : ", data);
	
	        },
	        error: function (e) {
	            console.log("ERROR : ", e);
	        },
	        complete: function() {
	        	$("#callbook-status").html("");
	    	}
	    });
	    $.ajax({
	        type: "GET",
	        url: "/log/search/" + callsign,
	        cache: false,
	        timeout: 600000,
	        success: function (data) {
	        	var previous_contact_list = "<b>Previous Contacts </b><span class='w3-badge w3-scout w3-theme-info'>" + data.length + "</span><br />";
	        	$.each(data, function(index, value) {
	        		previous_contact_list = previous_contact_list + value.timestamp + '<br />';
	        	});
	        	$('#previous-contacts').html(previous_contact_list);
	        	$('#previous-contacts-count').html(data.length);
	            console.log("AJAX RESULT : ", data);
	        },
	        error: function (e) {
	            console.log("ERROR : ", e);
	        },
	        complete: function() {
	        	$("#previous-contacts-status").html("");
	        }
	    });
	}
}

function show_info_box() {
	$('.info-box').animate({
		bottom: 0
	});
}

function hide_info_box() {
	$('.info-box').animate({
		bottom: 0 - $('.info-box').height() - 5
	});
}

var contactInputTimer;

function updateLiveEditMode() {
	if ( document.getElementById("liveSwitch").checked ) {
		// Live mode
		console.log("Started Live Logging");
		contactInputTimer = setInterval(function() {
			document.getElementById("timestamp").value = moment().utc().format('YYYY-MM-DD HH:mm:ss');
		}, 1000);
	} else {
		// Edit mode
		console.log("Stopping live logging")
		clearInterval(contactInputTimer);
	}
}

function openTab(panelBlock, tabName) {
	  var i;
	  var x = document.getElementsByClassName(panelBlock);
	  for (i = 0; i < x.length; i++) {
	    x[i].style.display = "none";
	  }
	  document.getElementById(tabName).style.display = "block";
}

var leftMenuOpen = false;	// Default to closed
var rightMenuOpen = false;	// Default to closed


function toggleRightMenu() {
	if ( rightMenuOpen ) {
		closeRightMenu();
	} else {
		openRightMenu();
	}
}

function openRightMenu() {
  document.getElementById("rightMenu").style.display = "block";
  document.getElementById("right-menu-button").innerHTML = "<i class=\"fas fa-times w3-large\"></i>";
  rightMenuOpen = true;
}

function closeRightMenu() {
  document.getElementById("rightMenu").style.display = "none";
  document.getElementById("right-menu-button").innerHTML = "<i class=\"fa fa-globe w3-large\"></i>";
  rightMenuOpen = false;
}

function toggleLeftMenu() {
	if ( leftMenuOpen ) {
		closeLeftMenu();
	} else {
		openLeftMenu();
	}
}

function openLeftMenu() {
	document.getElementById("page-overlay").style.display = "block";
	document.getElementById("leftMenu").style.display = "block";
	document.getElementById("left-menu-button").innerHTML = "<i class=\"fas fa-times w3-large\"></i>";
	leftMenuOpen = true;
}

function closeLeftMenu() {
	document.getElementById("page-overlay").style.display = "none";
	document.getElementById("leftMenu").style.display = "none";
	document.getElementById("left-menu-button").innerHTML = "<i class=\"fas fa-bars w3-large\"></i>";
	leftMenuOpen = false;
}

function toggleSmallPanel(header_id, target_id) {
	
	  var targetPanel = document.getElementById(target_id);
	  var targetHeader = document.getElementById(header_id);
	
	  if (targetPanel.className.indexOf("w3-hide-small") == -1) {
		  targetPanel.className += " w3-hide-small";
		  targetHeader.innerHtml = "<i class=\"fas fa-chevron-up\"></i> Hide Log Input";
	  } else {
		  targetPanel.className = targetPanel.className.replace(" w3-hide-small", "");
		  targetHeader.innerHtml = "<i class=\"fas fa-chevron-down\"></i> Show Log Input";
	  }
	}

function toggleElement(id) {
	var x = document.getElementById(id);
	  if (x.className.indexOf("w3-hide") == -1) {
		  x.className += "w3-hide";
	  } else {
		  x.className = x.className.replace("w3-hide", "");
	  }
}

function checkLog(callsign) {
	$('#checkLogResults').empty();
	$.ajax({
        type: "GET",
        url: "/search/" + callsign,
        cache: false,
        timeout: 600000,
        success: function (data) {
        	$('#checkLogResults').empty();
        	if ( data.length < 1 ) {
        		$('#checkLogResults').html("Sorry, nothing in the log");
        	}
        	$.each(data, function(index, value) {
        		$('#checkLogResults').append("<tr><td>" + value.timestamp + "</td><td>" + value.callsign + "</td><td>" + value.band + "</td><td>" + value.mode + "</td></tr>");
        	});
        },
        error: function (e) {
            console.log("ERROR : ", e);
        },
        complete: function() {
        	$("#callbook-status").html("");
    	}
    });	
}

function changePassword() {
	var password1 = $('#password1').val();
	var password2 = $('#password2').val();
	
	if ( password1 != password2 ) {
		$('#error-message').html("Error: passwords do not match!");
		$('#modal-messages').css("display", "block");
		
		return false;
	}
	
	return true;
}

function showModal(id, url) {
	document.getElementById(id).style.display='block';
	var submitElements = document.getElementsByClassName("modal-url");
	for(var i = 0; i < submitElements.length; i++)
	{
	   submitElements.item(i).href=url;
	   submitElements.item(i).action=url;
	}
}

function hideModal(id) {
	document.getElementById(id).style.display='none';
}