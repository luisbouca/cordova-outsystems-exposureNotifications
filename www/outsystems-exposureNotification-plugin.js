var exec = require('cordova/exec');

exports.start = function (success, error) {
    exec(success, error, 'ExposureNotifications', 'start', []);
};
exports.stop = function (success, error) {
    exec(success, error, 'ExposureNotifications', 'stop', []);
};

exports.retrieve = function (success, error) {
    exec(success, error, 'ExposureNotifications', 'retrieve', []);
};
exports.provide = function (jsonArrayBase64Zips, success, error) {
    exec(success, error, 'ExposureNotifications', 'provide', [jsonArrayBase64Zips]);
};

exports.setListener = function (success, error) {
    exec(success, error, 'ExposureNotifications', 'setListener', []);
};