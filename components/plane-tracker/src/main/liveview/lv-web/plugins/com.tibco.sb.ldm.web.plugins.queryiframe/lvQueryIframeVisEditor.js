'use strict';
angular.module('com.tibco.sb.ldm.web.plugins.queryiframe').directive(
	'lvQueryIframeVisEditor',
	['$log', function($log){
		return {
			restrict: 'A',
			templateUrl: 'plugins/com.tibco.sb.ldm.web.plugins.queryiframe/queryIframeVisEditor.tpl.html',
			scope: {
				model: '=',
				updateModel: '&'
			},
			controller: function($scope){

			}
		};
	}]
);
