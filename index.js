var __extends = (this && this.__extends) || (function () {
    var extendStatics = Object.setPrototypeOf ||
        ({ __proto__: [] } instanceof Array && function (d, b) { d.__proto__ = b; }) ||
        function (d, b) { for (var p in b) if (b.hasOwnProperty(p)) d[p] = b[p]; };
    return function (d, b) {
        extendStatics(d, b);
        function __() { this.constructor = d; }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
})();
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
import { Injectable } from '@angular/core';
import { Cordova, IonicNativePlugin, Plugin } from '@ionic-native/core';
/**
 * @name @ionic-native/ Multi Camera
 * @description
 * This plugin does something
 *
 * @usage
 * ```typescript
 * import { MultiCamera } from '@ionic-native/ionic-native-multi-camera';
 *
 *
 * constructor(private multiCamera: MultiCamera) { }
 *
 * ...
 *
 *
 * this.MultiCamera.functionName('Hello', 123)
 *   .then((res: any) => console.log(res))
 *   .catch((error: any) => console.error(error));
 *
 * ```
 */
var MultiCamera = (function (_super) {
    __extends(MultiCamera, _super);
    function MultiCamera() {
        return _super !== null && _super.apply(this, arguments) || this;
    }
    /**
     * open 7camera
     * @return {Promise<any>} Returns a promise that resolves when something happens
     */
    /**
       * open 7camera
       * @return {Promise<any>} Returns a promise that resolves when something happens
       */
    MultiCamera.prototype.open = /**
       * open 7camera
       * @return {Promise<any>} Returns a promise that resolves when something happens
       */
    function (options) {
        return; // We add return; here to avoid any IDE / Compiler errors
    };
    MultiCamera.decorators = [
        { type: Injectable },
    ];
    __decorate([
        Cordova({ callbackOrder: 'reverse' }),
        __metadata("design:type", Function),
        __metadata("design:paramtypes", [Object]),
        __metadata("design:returntype", Promise)
    ], MultiCamera.prototype, "open", null);
    /**
     * @name @ionic-native/ Multi Camera
     * @description
     * This plugin does something
     *
     * @usage
     * ```typescript
     * import { MultiCamera } from '@ionic-native/ionic-native-multi-camera';
     *
     *
     * constructor(private multiCamera: MultiCamera) { }
     *
     * ...
     *
     *
     * this.MultiCamera.functionName('Hello', 123)
     *   .then((res: any) => console.log(res))
     *   .catch((error: any) => console.error(error));
     *
     * ```
     */
    MultiCamera = __decorate([
        Plugin({
            pluginName: 'MultiCamera',
            plugin: 'cordova-plugin-multicamera',
            // npm package name, example: cordova-plugin-camera
            pluginRef: 'MultiCamera',
            // the variable reference to call the plugin, example: navigator.geolocation
            repo: 'https://github.com/AndreAbascal/Cordova-Plugin-Multicamera',
            // the github repository URL for the plugin
            platforms: ['Android'] // Array of platforms supported, example: ['Android', 'iOS']
        })
    ], MultiCamera);
    return MultiCamera;
}(IonicNativePlugin));
export { MultiCamera };
//# sourceMappingURL=index.js.map