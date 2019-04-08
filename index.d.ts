import { IonicNativePlugin } from '@ionic-native/core';
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
export declare class MultiCamera extends IonicNativePlugin {
    /**
     * open 7camera
     * @return {Promise<any>} Returns a promise that resolves when something happens
     */
    open(options: any): Promise<any>;
}
