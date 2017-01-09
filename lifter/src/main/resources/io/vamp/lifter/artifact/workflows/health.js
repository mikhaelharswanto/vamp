'use strict';

var _ = require('lodash');
var vamp = require('vamp-node-client');

var api = new vamp.Api();
var metrics = new vamp.Metrics(api);

var period = 5;  // seconds
var window = 30; // seconds

function health(lookupName, tag) {

    var errorCode = 500;
    var term = {ft: lookupName};
    var range = {ST: {gte: errorCode}};

    metrics.count(term, range, window, function (total) {
        api.event([tag, 'health'], total > 0 ? 0 : 1);
    });
}

var process = function() {

  api.gateways(function (gateways) {
      _.forEach(gateways, function (gateway) {
          health(gateway.lookup_name, 'gateways:' + gateway.name);
      });
  });

  api.deployments(function (deployments) {
      _.forEach(deployments, function (deployment) {
          _.forOwn(deployment.clusters, function (cluster) {
              _.forOwn(cluster.gateways, function (gateway) {
                  health(gateway.lookup_name, 'deployments:' + deployment.name);
              });
          });
      });
  });
};

setInterval(process, period * 1000);
