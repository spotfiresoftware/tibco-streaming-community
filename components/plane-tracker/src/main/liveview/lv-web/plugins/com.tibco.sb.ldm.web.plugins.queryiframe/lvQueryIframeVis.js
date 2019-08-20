;(function(angular){
	'use strict';
	angular
		.module('com.tibco.sb.ldm.web.plugins.queryiframe')
		.directive('lvQueryIframeVis', lvQueryIframeVis);

	lvQueryIframeVis.$inject = ['$log', 'FormatterService', 'VisualizationContract','$sce'];

	function lvQueryIframeVis($log, FormatterService, VisualizationContract, $sce){

		var logger = $log.getInstance('/com.tibco.sb.ldm/web/plugins/queryiframe/lvQueryIframeVis');

		return {
			restrict: 'A',
			templateUrl: 'plugins/com.tibco.sb.ldm.web.plugins.queryiframe/queryIframeVis.tpl.html',
			scope: false,
			controller: lvQueryIframeVisCtrl
		};

		function lvQueryIframeVisCtrl($scope, $element){
			//$scope represents the scope which LiveView Web provides to the visualization
			//$scope comes pre-populated with the following properties
			//contract 		- 	'contract' is an object which facilitate two-way communication between LiveView Web
			// 					and the visualization.
			//visualization - 	this is the config object which the visualization editor generates during editing phase.
			// 					The structure of this object is designed by the visualization plugin developer. It should
			// 					contain all the information the plugin developer needs to render the visualization including
			// 					which datasets (identified by their ids) to render and how to render them. Without the
			// 					datasetIds, the visualization will not know which data is to be rendered

			//The function which is to be called to unsubscribe a dataset subscription
			var dataSetUnsubscriberFunc;

			var formatters = {};

			//a scope variable which is used in the html template to show the fields
			$scope.fields = [];
			//a scope variable which is used in the html template to show the field values
			$scope.values = [];

			//the 'contract' contains the following functions, which the visualization needs to implement
			//not all functions need implementation, some are provided with default implementations
			//the functions in the contract are

			// onConfigChanged		-	Called by LiveView Web when the visualization's config has changed. The config
			// 							can change during an editing cycle or via other means (like card decorators).

			// onDatasetsChanged	- 	Called by LiveView Web when the dataset configuration has changed. The configuration
			// 							can change during editing cycle or via other means (like card decorators).
			//							Arguments
			//								addedDatasetIds			-	The ids of the added datasets
			// 								updatedDatasetIds		-	The ids of the updated datasets
			// 								deletedDatasetIds		-	The ids of the deleted datasets

			// subscribeToDataset	- 	Provided by LiveView Web for visualization to start dataset subscription. Returns
			//							a function to unsubscribe. By default, data is buffered. When buffering is on,
			// 							onDataAdded, onDataUpdate & onDataRemoved are not called, instead the onDataSet
			// 							function is called with all the events buffered from the last call to onDataSet.
			//							Buffering can be switched off by passing {disableBuffering: true} as settings.
			//							Arguments
			//								datasetId				-	The id of the dataset
			//								settings				-  	Any additional settings. Currently


			// onSchemaSet			- 	Called by LiveView Web to provide the schema of a dataset
			//							Arguments
			//								datasetId				-	The id of the dataset
			// 								queryEventData
			//										schema			-	The schema of the dataset
			//										subscription	-	The LiveView.QuerySubscription object for the
			// 															subscribed query

			// onData				- 	Called by LiveView Web to provide data events for the subscribed data source.
			//							This callback is invoked whenever an add, update, or delete event is received
			//							for the data source if buffering is not enabled. If buffering is enabled (and it
			//							is by default), then this function is called when the buffer gets flushed.
			//							Arguments
			//								dataSourceId			- 	The id of the dataset
			// 								dataStore				-   Reference to coalesced data for the data source
			//								dataEvents	 			-	The data events. Each event has the following
			//															properties
			//															type - The type of the event. Can be one of:
			//																VisualizationContract.DataEvent.Types.ADD
			//																VisualizationContract.DataEvent.Types.UPDATE
			//																VisualizationContract.DataEvent.Types.DELETE
			//															data - The data payload of the event. For
			//																LiveView data sources, this is the tuple
			//																that was affected by the add/update/remove
			//															affectedPositions - An object containing two
			// 																properties: oldIndex and newIndex. This info
			//																can be used to correlate positions between
			//																the data that's in the dataStore and data
			//																that's in your visualization model. On add
			//																events, newIndex is the position where the
			//																data was inserted and oldIndex will be -1.
			// 																On update events, oldIndex will be the
			// 																position of the data before the update was
			// 																applied and newIndex will be the position of
			// 																the data after the update was applied. On
			//																delete events, newIndex will be -1 and
			//																oldIndex will be the position of the data
			//																before it was removed. This information is
			//																critical for those visualizations that want
			//																to ensure consistency with ordered data
			//																sources (e.g. those resulting from LiveView
			//																queries with an ORDER BY clause).

			// onError				- 	Called by LiveView Web when an error occurs during query execution and updates.
			// 							LiveView provides a default implementation which shows a dialog. Plugin developers
			// 							can override that behavior by providing their own implementation
			//							Arguments
			//								datasetId				-	The id of the dataset
			// 								errorMsg				-	The error message


			// handleVizError		- 	Provided by LiveView Web for visualization for error handling during the visualization
			//							processing
			//							Arguments
			//								error					- 	The error, can be string or Error object
			//								show					-	Shows the error to user if true
			//								context					-	An object which is logged as contextual information

			// handleDataSelected	-	Provided by LiveView Web for visualization to indicate data/marker selection
			//							Arguments datasetId, tuple, categoryField, valueField
			//								dataSetId				-	The id of the dataset driving the selected data/marker
			//								tuple					-	The acutal tuple driving the selected data/marker
			//								categoryField			-	An optional field which represent category axis
			// 															value in the tuple
			//								valueField				-	An optional field which represent value axis
			// 															value in the tuple

			//add implementations for required functions
			$scope.contract.onConfigChanged = handleConfigChanged;
			$scope.contract.onDatasetsChanged = handleDatasetsChanged;
			$scope.contract.onSchemaSet = handleSchemaSet;
			$scope.contract.onData = handleData;

			//subscribe to data with default settings, buffering is enabled. To disable buffering comment out next line
			//and uncomment the line below it
			dataSetUnsubscriberFunc = $scope.contract.subscribeToData($scope.visualization.config.datasetId);
			//dataSetUnsubscriberFunc = $scope.contract.subscribeToData($scope.visualization.config.datasetId, {disableBuffering: true});

			function handleConfigChanged(){
				//reprocess the $scope.visualization object to make appropriate changes to the rendering
				//called when the 'apply' button is clicked in the visualization tab in the card editor
			}

			function handleDatasetsChanged(addedDataSourceIds, updatedDataSourceIds, deletedDataSourceIds){
				//addedDataSourceIds are only for information
				//updateDatasetIds are to be processed, all markers driven by any of these ids need to removed from the visualization
				//deletedDataSourceIds are to be processed, all markers driven by any of these ids need to removed from the visualization
				//called when the 'apply' button is clicked in the data tab in the card editor
				$scope.fields = [];
				formatters = {};
				$scope.values = [];
			}

			function handleSchemaSet(dataSourceId, queryEventData){
				//ideally should validate configuration to make sure the fields used for extraction of values from dataset
				//actually exist in the schema
				angular.forEach(queryEventData.schema.fields, function (field) {
					$scope.fields.push(field.name);
					if (field.type === 'double'){
						formatters[field.name] = FormatterService.compile('${'+field.name+'|number:2}', queryEventData.schema);
					}
				});
			}

			function handleData(dataSourceId, dataStore, dataEvents){
				var i;
				for(i = 0; i < dataEvents.length; i++){
					if(dataEvents[i].type === VisualizationContract.DataEvent.Types.DELETE){
						handleDeletedData(dataEvents[i].data);
					}
				}
				for(i = 0; i < dataEvents.length; i++){
					if(dataEvents[i].type === VisualizationContract.DataEvent.Types.ADD){
						handleNewData(dataEvents[i].data);
					}
					else if(dataEvents[i].type === VisualizationContract.DataEvent.Types.UPDATE){
						handleUpdateData(dataStore, dataEvents[i].data);
					}
				}
			}

			function handleNewData(newData){
				//render the tuple as a new marker
				angular.forEach($scope.fields, function(fieldName){
					if(formatters[fieldName]){
						$scope.values[fieldName] = formatters[fieldName](newData);
					}
					else {
						var flightID = newData.fieldMap[fieldName];
						if(flightID!=null){
							$scope.values[fieldName] = $sce.trustAsResourceUrl('https://flightaware.com/live/flight/'+flightID);
						}else{
							$scope.values[fieldName] = $sce.trustAsResourceUrl('https://flightaware.com/');
						}
					}
				});
			}

			function handleUpdateData(dataStore, updateData){
				//update an existing marker with new values
				//Note that updateData only contains fields that changed, but we need all fields to update our
				// visualization. So, we lookup the full version of the data in the dataStore, using the updateData's
				// id as the index.
				angular.forEach($scope.fields, function(fieldName){
					if(formatters[fieldName]){
						$scope.values[fieldName] = formatters[fieldName](dataStore.map[updateData.id]);
					}
					else {
						var flightID2 = dataStore.map[updateData.id].fieldMap[fieldName];
						if(flightID2!=null){
							$scope.values[fieldName] = $sce.trustAsResourceUrl('https://flightaware.com/live/flight/'+flightID2);
						}else{
							$scope.values[fieldName] = $sce.trustAsResourceUrl('https://flightaware.com/');
						}
					}
				});
			}

			function handleDeletedData(deleteData){
				//remove an existing marker
				angular.forEach($scope.fields, function(fieldName){
					$scope.values[fieldName] = '';
				});
			}

			$scope.$on('$destroy', function(event){
				//do visualization clean up and unsubscribe from all subscribed datasets
				dataSetUnsubscriberFunc();
			});

		}

	}

})(angular);
