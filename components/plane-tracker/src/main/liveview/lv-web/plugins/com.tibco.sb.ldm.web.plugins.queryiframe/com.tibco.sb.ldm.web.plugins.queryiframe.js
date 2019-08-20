;(function(angular){
	'use strict';

	angular
		.module('com.tibco.sb.ldm.web.plugins.queryiframe', ['com.tibco.sb.ldm'])
		.run(onModuleRun);

	onModuleRun.$inject = ['PluginRegistry'];
	function onModuleRun(PluginRegistry){
		PluginRegistry.register(
			new PluginRegistry.VisualizationPlugin('queryIframe', 'lv-query-iframe-vis', 'lv-query-iframe-vis-editor', {
				name: 'Query iFrame', //Limit length to 12 characters
				defaultConfig: {
					datasetId: '%datasetId%'
				},
				previewIconUrl: 'plugins/com.tibco.sb.ldm.web.plugins.queryiframe/iframeicon_60x60_360.png' //TODO: change this to an icon in your plug-in
			})
		);
	}

})(angular);