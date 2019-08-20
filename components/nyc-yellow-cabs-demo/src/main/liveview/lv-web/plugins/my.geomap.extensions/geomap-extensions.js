/**
 * Created by avaldez on 2/15/17.
 */
;(function(angular){
	'use strict';

	angular
		.module('my.geomap.extensions', ['com.tibco.sb.ldm'])
		.run(onModuleRun);

	onModuleRun.$inject = ['geomap.config'];

	function onModuleRun(GeoMapConfig) {

		//Adds new regions to the geo-map visualization editor
		var originalRegions = GeoMapConfig.regions;

		var myCustomRegions = [{
			name: 'Mexico City',
			lat: 19.40737513756272,
			lon: -99.15212888812046,
			zoomLevel: 11
		},{
			name: 'Dallas',
			lat: 32.77258032734045,
			lon: -96.81822080706583,
			zoomLevel: 11
		},{
			name: 'Sydney',
			lat: -33.86329220105064,
			lon: 151.17286273271438,
			zoomLevel: 12
		}];

		var regionsOverride = originalRegions.concat(myCustomRegions);

		//Sets the icons for the geo-map markers
		//We must overwrite the basePath, so we can't use the the original icons unless we include them in this plugin too.
		var iconsOverride = {
			basePath: 'plugins/my.geomap.extensions/custom-icons/',
			files: [
				//The original icons
				'pin.png',
				'walker.png',
				'car_green.png',
				'car_red.png',
				'car.png',
				'shipping.png',
				'bus.png',
				'train.png',
				'ship.png',
				'airplane.png',
				'mail.png',
				'pin_dark.png',
				'pin_cyan.png',
				'pin_green.png',
				'pin_yellow.png',
				'pin_orange.png',
				'pin_pink.png',
				'pin_red.png'
			]
		};

		GeoMapConfig.overrideSettings({
			regions: regionsOverride,
			icons: iconsOverride
		});

	}

}(angular));
