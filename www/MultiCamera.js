var exec = require('cordova/exec');

var service = "MultiCamera";

exports.open = function(success,error,params){
	console.log("MultiCamera.js... openCamera params: ",params);
	exec(success, error, service, 'open', [params]);
};