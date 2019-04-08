import Foundation;
import AVFoundation;
import CoreMotion;

extension BinaryInteger {
    var degreesToRadians: CGFloat { return CGFloat(Int(self)) * .pi / 180 }
}

extension UIImage {
    func rotate(radians: Float) -> UIImage? {
        var newSize = CGRect(origin: CGPoint.zero, size: self.size).applying(CGAffineTransform(rotationAngle: CGFloat(radians))).size;
        // Trim off the extremely small float value to prevent core graphics from rounding it up
        newSize.width = floor(newSize.width);
        newSize.height = floor(newSize.height);
        
        UIGraphicsBeginImageContextWithOptions(newSize, true, self.scale);
        let context = UIGraphicsGetCurrentContext()!;
        
        // Move origin to middle
        context.translateBy(x: newSize.width/2, y: newSize.height/2);
        // Rotate around middle
        context.rotate(by: CGFloat(radians));
        // Draw the image at its center
        self.draw(in: CGRect(x: -self.size.width/2, y: -self.size.height/2, width: self.size.width, height: self.size.height));
        
        let newImage = UIGraphicsGetImageFromCurrentImageContext();
        UIGraphicsEndImageContext();
        
        return newImage;
    }
}

extension UIImage.Orientation {
    
    init(_ cgOrientation: CGImagePropertyOrientation) {
        // we need to map with enum values becuase raw values do not match
        switch cgOrientation {
        case .up: self = .up
        case .upMirrored: self = .upMirrored
        case .down: self = .down
        case .downMirrored: self = .downMirrored
        case .left: self = .left
        case .leftMirrored: self = .leftMirrored
        case .right: self = .right
        case .rightMirrored: self = .rightMirrored
        }
    }
    
    
    /// Returns a UIImage.Orientation based on the matching cgOrientation raw value
    static func orientation(fromCGOrientationRaw cgOrientationRaw: UInt32) -> UIImage.Orientation? {
        var orientation: UIImage.Orientation?
        if let cgOrientation = CGImagePropertyOrientation(rawValue: cgOrientationRaw) {
            orientation = UIImage.Orientation(cgOrientation)
        } else {
            orientation = nil // only hit if improper cgOrientation is passed
        }
        return orientation
    }
}

class CameraViewController: UIViewController {
    let captureSession = AVCaptureSession()
    // var activityIndicator: UIActivityIndicatorView!
    var device: AVCaptureDevice!
    var cameraPreview: PreviewView!
    var captureButton: CaptureButton!
    var output = AVCapturePhotoOutput()
    var gravity = CameraVideoGravity.resizeAspect;
    var captureVideoQuality = AVCaptureSession.Preset.high;
	var photos = [String]();
    var photoLabel = UILabel();

    public var finish: (([String]) -> ())?;
    
    var orientationLast = UIInterfaceOrientation(rawValue: 0)!;
    var motionManager: CMMotionManager = {
        return CMMotionManager()
    }();
    
    @objc func cancel(sender: UIButton) {
        dismiss(animated: true) {
            self.motionManager.stopAccelerometerUpdates();
            self.finish?([]);
        }
    }
    
    func debugImageOrientation(imageOrientation: UIImageOrientation) -> String {
        switch imageOrientation {
            case .down:
                return "down";
            case .downMirrored:
                return "downMirrored";
            case .left:
                return "left";
            case .leftMirrored:
                return "leftMirrored";
            case .right:
                return "right";
            case .rightMirrored:
                return "rightMirrored";
            case .up:
                return "up";
            case .upMirrored:
                return "upMirrored";
        }

    }
    
    func findEquivalentImageOrientation(orientation: UIInterfaceOrientation) -> UIImageOrientation{
        switch orientation {
            case .portrait, .unknown:
                return UIImageOrientation.up;
            case .portraitUpsideDown:
                return UIImageOrientation.down;
            case .landscapeLeft:
                return UIImageOrientation.left;
            case .landscapeRight:
                return UIImageOrientation.right;
        }
    }
    
    func findEquivalentInterfaceOrientation(orientation: UIImageOrientation) -> UIInterfaceOrientation{
        switch orientation {
            case .up, .upMirrored:
                return UIInterfaceOrientation.portrait;
            case .down, .downMirrored:
                return UIInterfaceOrientation.portraitUpsideDown;
            case .left, .leftMirrored:
                return UIInterfaceOrientation.landscapeLeft;
            case .right, .rightMirrored:
                return UIInterfaceOrientation.landscapeRight;
        }
    }
    
    func findInterfaceOrientationDegrees(orientation: UIInterfaceOrientation) -> Int{
        switch orientation {
            case .portrait:
                return 0;
            case .portraitUpsideDown:
                return 180;
            case .landscapeLeft:
                return 270;
            case .landscapeRight:
                return 90;
            case .unknown:
                return 0;
        }
    }

	func fixOrientationOfImage(image: UIImage) -> UIImage? {
		if image.imageOrientation == .up {
			return image
		}

		// We need to calculate the proper transformation to make the image upright.
		// We do it in 2 steps: Rotate if Left/Right/Down, and then flip if Mirrored.
		var transform = CGAffineTransform.identity

		switch image.imageOrientation {
			case .down, .downMirrored:
				transform = transform.translatedBy(x: image.size.width, y: image.size.height);
				transform = transform.rotated(by: CGFloat(Double.pi));
				break;
			case .left, .leftMirrored:
				transform = transform.translatedBy(x: image.size.width, y: 0);
				transform = transform.rotated(by:  CGFloat(Double.pi / 2));
				break;
			case .right, .rightMirrored:
				transform = transform.translatedBy(x: 0, y: image.size.height);
				transform = transform.rotated(by:  -CGFloat(Double.pi / 2));
				break;
			default:
				break
		}

		switch image.imageOrientation {
			case .upMirrored, .downMirrored:
				transform = transform.translatedBy(x: image.size.width, y: 0);
				transform = transform.scaledBy(x: -1, y: 1);
				break;
			case .leftMirrored, .rightMirrored:
				transform = transform.translatedBy(x: image.size.height, y: 0);
				transform = transform.scaledBy(x: -1, y: 1);
				break;
			default:
				break
		}

		// Now we draw the underlying CGImage into a new context, applying the transform
		// calculated above.
		guard let context = CGContext(data: nil, width: Int(image.size.width), height: Int(image.size.height), bitsPerComponent: image.cgImage!.bitsPerComponent, bytesPerRow: 0, space: image.cgImage!.colorSpace!, bitmapInfo: image.cgImage!.bitmapInfo.rawValue) else {
			return nil
		}

		context.concatenate(transform)

		switch image.imageOrientation {
		case .left, .leftMirrored, .right, .rightMirrored:
			context.draw(image.cgImage!, in: CGRect(x: 0, y: 0, width: image.size.height, height: image.size.width))
		default:
			context.draw(image.cgImage!, in: CGRect(origin: .zero, size: image.size))
		}

		// And now we just create a new UIImage from the drawing context
		guard let CGImage = context.makeImage() else {
			return nil
		}

		return UIImage(cgImage: CGImage)
	}
    
    func fixOrientationOfImageAccordingToDevice(image: UIImage, deviceOrientation: UIInterfaceOrientation) -> UIImage? {
        let equivalentOrientation = findEquivalentInterfaceOrientation(orientation: image.imageOrientation);
        if equivalentOrientation == deviceOrientation {
            return image;
        }
        
        // We need to calculate the proper transformation to make the image upright.
        // We do it in 2 steps: Rotate if Left/Right/Down, and then flip if Mirrored.
        var transform = CGAffineTransform.identity;
        switch deviceOrientation {
            case .unknown:
                return image;
            case .landscapeRight:
                return image;
            case .landscapeLeft:
                transform = transform.translatedBy(x: image.size.width, y: image.size.height);
                transform = transform.rotated(by: CGFloat(Double.pi));
                break;
            case .portrait:
                transform = transform.translatedBy(x: 0, y: image.size.height);
                transform = transform.rotated(by:  -CGFloat(Double.pi / 2));
                break;
            case .portraitUpsideDown:
                transform = transform.translatedBy(x: 0, y: image.size.height);
                transform = transform.rotated(by:  CGFloat(Double.pi / 2));
                break;
        }
        
        // Now we draw the underlying CGImage into a new context, applying the transform
        // calculated above.
        guard let context = CGContext(data: nil, width: Int(image.size.width), height: Int(image.size.height), bitsPerComponent: image.cgImage!.bitsPerComponent, bytesPerRow: 0, space: image.cgImage!.colorSpace!, bitmapInfo: image.cgImage!.bitmapInfo.rawValue) else {
            return nil
        }
        
        context.concatenate(transform)
        
        switch deviceOrientation {
            case .landscapeLeft, .landscapeRight:
                context.draw(image.cgImage!, in: CGRect(x: 0, y: 0, width: image.size.height, height: image.size.width));
            default:
                context.draw(image.cgImage!, in: CGRect(origin: .zero, size: image.size));
        }
        
        // And now we just create a new UIImage from the drawing context
        guard let CGImage = context.makeImage() else {
            return nil;
        }
        
        return UIImage(cgImage: CGImage);
    }
    
    func initializeMotionManager() {
        let opQueue = OperationQueue.init();
        motionManager.accelerometerUpdateInterval = 0.2;
        motionManager.gyroUpdateInterval = 0.2;
        motionManager.startAccelerometerUpdates(to: opQueue, withHandler: {
            (accelerometerData, error) -> Void in
            if error == nil {
                self.outputAccelerationData((accelerometerData?.acceleration)!)
            }
            else {
                print("\(error!)")
            }
        })
    }
    
    override func loadView() {
        self.view = UIView(frame: UIScreen.main.bounds);
        self.view.backgroundColor = UIColor.black;
        let frame = CGRect(x: 0, y: 0, width: UIScreen.main.bounds.size.width, height: UIScreen.main.bounds.size.height);
        cameraPreview = PreviewView(frame: frame, session: captureSession, videoGravity: gravity);
        self.view.addSubview(cameraPreview);
        let header = UIView();
//        header.backgroundColor = UIColor.red;
        header.translatesAutoresizingMaskIntoConstraints = false;
        self.view.addSubview(header);
        let headerHeight: CGFloat = 64;
        let labelFrame = CGRect(x: 0, y: 0, width: 80, height: Int(headerHeight));
        photoLabel.frame = labelFrame;
        photoLabel.center = CGPoint(x: Int(UIScreen.main.bounds.width)/2, y: Int(headerHeight)/2);
        photoLabel.font = UIFont.preferredFont(forTextStyle: .body);
        photoLabel.textColor = UIColor.white;
        photoLabel.textAlignment  = .center;
        header.addSubview(photoLabel);
        NSLayoutConstraint(item: header, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1.0, constant: headerHeight).isActive = true;
        NSLayoutConstraint(item: header, attribute: .leading, relatedBy: .equal, toItem: view, attribute: .leading, multiplier: 1.0, constant: 0).isActive = true;
        NSLayoutConstraint(item: header, attribute: .trailing, relatedBy: .equal, toItem: view, attribute: .trailing, multiplier: 1.0, constant: 0).isActive = true;
        NSLayoutConstraint(item: header, attribute: .top, relatedBy: .equal, toItem: view, attribute: .top, multiplier: 1.0, constant: 0).isActive = true;
        let footer = UIView();
        footer.translatesAutoresizingMaskIntoConstraints = false;
        self.view.addSubview(footer);
        let footerHeight: CGFloat = 80;
        NSLayoutConstraint(item: footer, attribute: .height, relatedBy: .equal, toItem: nil, attribute: .notAnAttribute, multiplier: 1.0, constant: footerHeight).isActive = true;
        NSLayoutConstraint(item: footer, attribute: .leading, relatedBy: .equal, toItem: view, attribute: .leading, multiplier: 1.0, constant: 0).isActive = true;
        NSLayoutConstraint(item: footer, attribute: .trailing, relatedBy: .equal, toItem: view, attribute: .trailing, multiplier: 1.0, constant: 0).isActive = true;
        NSLayoutConstraint(item: footer, attribute: .bottom, relatedBy: .equal, toItem: view, attribute: .bottom, multiplier: 1.0, constant: 0).isActive = true;
        captureButton = CaptureButton(frame: footer.frame);
        captureButton.center = CGPoint(x: Int(UIScreen.main.bounds.width)/2, y: Int(footerHeight)/2);
        let cancelButton = UIButton(type: .system);
        cancelButton.frame = CGRect(x: 0, y: 0, width: 80, height: Int(footerHeight));
        cancelButton.setTitle("Cancelar", for: .normal);
        cancelButton.setTitleColor(UIColor.white, for: UIControlState.normal);
        cancelButton.isEnabled = true;
        cancelButton.addTarget(self, action: #selector(self.cancel(sender:)), for: UIControlEvents.touchUpInside);
        footer.addSubview(cancelButton);
        let okButton = UIButton(type: .system);
        okButton.frame = CGRect(x: 0, y: 0, width: 40, height: Int(footerHeight));
        okButton.setTitle("OK", for: .normal);
        okButton.setTitleColor(UIColor.white, for: UIControlState.normal);
        okButton.isEnabled = true;
        okButton.addTarget(self, action: #selector(self.ok(sender:)), for: UIControlEvents.touchUpInside);
        footer.addSubview(okButton);
        okButton.center = CGPoint(x: Int(UIScreen.main.bounds.width)-40, y: Int(footerHeight)/2);
        footer.addSubview(captureButton);
        statusAuthorize();
    }
    
    @objc func ok(sender: UIButton) {
        dismiss(animated: true) {
            self.motionManager.stopAccelerometerUpdates();
            self.finish?(self.photos);
        }
    }
    
    func outputAccelerationData(_ acceleration: CMAcceleration) {
        var orientationNew: UIInterfaceOrientation
        if acceleration.x >= 0.75 {
            orientationNew = .landscapeRight;
        }
        else if acceleration.x <= -0.75 {
            orientationNew = .landscapeLeft;
        }
        else if acceleration.y <= -0.75 {
            orientationNew = .portrait;
        }
        else if acceleration.y >= 0.75 {
            orientationNew = .portraitUpsideDown;
        }
        else {
            // Consider same as last time
            return;
        }
        
        if orientationNew == orientationLast {
            return;
        }
        orientationLast = orientationNew;
    }
    
    override var shouldAutorotate: Bool {
        return false;
    }
    
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        return .portrait;
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator);
    }
    
    override func viewDidLoad() {
        super.viewDidLoad();
        DispatchQueue.global().async {
            self.initializeMotionManager();
            self.sessionConfigure();
        }
    }
}

extension CameraViewController {
    func statusAuthorize() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            break
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video, completionHandler: { (granted) in
                if !(granted) {
                    self.settingDevice()
                }
            })
            break
        default:
            break
        }
    }
    
    func settingDevice() {
        DispatchQueue.main.async(execute: { [unowned self] in
            let message = NSLocalizedString("É necessário acesso a sua câmera para capturar as fotos.", comment: "Mensagem de alerta para quando o usuário negar acesso à câmera.");
            let alertController = UIAlertController(title: "O acesso a câmera foi negado.", message: message, preferredStyle: .alert)
            alertController.addAction(UIAlertAction(title: NSLocalizedString("OK", comment: "Botão de alerta OK"), style: .cancel, handler: { action in
                self.changeEnabledButton()
            }))
            alertController.addAction(UIAlertAction(title: NSLocalizedString("Settings", comment: "Botão de alerta para abrir as Definições"), style: .default, handler: { action in
                if let appSettings = URL(string: UIApplicationOpenSettingsURLString) {
                    UIApplication.shared.open(appSettings, options: [:], completionHandler: nil)
                }
            }))
            self.present(alertController, animated: true, completion: nil)
        })
    }
}

extension CameraViewController {
    func sessionConfigure() {
        self.captureSession.beginConfiguration()
        self.captureSession.sessionPreset = AVCaptureSession.Preset(rawValue: self.switchVideoQuality())
        self.device = AVCaptureDevice.default(for: .video)
        let input = try! AVCaptureDeviceInput(device: self.device)
        if self.captureSession.canAddInput(input) {
            self.captureSession.addInput(input)
        }
        output.setPreparedPhotoSettingsArray([AVCapturePhotoSettings(format: [AVVideoCodecKey : AVVideoCodecType.jpeg])], completionHandler: nil)
        self.captureSession.addOutput(output)
        self.captureSession.commitConfiguration()
        self.captureSession.startRunning()
    }
    
    func switchVideoQuality() -> String {
        var qualityValue = ""
        switch captureVideoQuality {
        case AVCaptureSession.Preset.high:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.cif352x288:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.hd1280x720:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.hd1920x1080:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.hd4K3840x2160:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.iFrame1280x720:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.iFrame960x540:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.low:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.medium:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.photo:
            qualityValue = captureVideoQuality.rawValue
        case AVCaptureSession.Preset.vga640x480:
            qualityValue = captureVideoQuality.rawValue
        default:
            qualityValue = AVCaptureSession.Preset.high.rawValue
        }
        return qualityValue
    }
}

extension CameraViewController: CameraButtonDelegate, AVCapturePhotoCaptureDelegate {
    override func viewDidAppear(_ animated: Bool) {
        captureButton.delegate = self
    }

    func takePicture() {
        let settings = AVCapturePhotoSettings();
        output.capturePhoto(with: settings, delegate: self);
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
		do {
            var deviceOrientation: UIInterfaceOrientation = orientationLast;
            var metadata = photo.metadata;
			if let imageData = photo.fileDataRepresentation() {
				let shutterView = UIView(frame: cameraPreview.frame);
				shutterView.backgroundColor = UIColor.black;
				view.addSubview(shutterView);
				UIView.animate(withDuration: 0.3, animations: {
					shutterView.alpha = 0;
				}, completion: { (_) in
					shutterView.removeFromSuperview();
				});
				let tempDirectory = FileManager.default.temporaryDirectory;
                let fileName = NSUUID().uuidString;
                let fileURL = tempDirectory.appendingPathComponent(fileName+".jpg");
                var imageUIImage: UIImage = UIImage(data: imageData)!;
                var degrees: Int = findInterfaceOrientationDegrees(orientation: deviceOrientation);
                imageUIImage = imageUIImage.rotate(radians: Float(Int(degrees).degreesToRadians))!;
                let imageData2: Data = UIImageJPEGRepresentation(imageUIImage,0.7)!;
                try? imageData2.write(to: fileURL, options: .atomic);
                photos.append(fileURL.absoluteString);
                photoLabel.text = photos.count > 1 ? String(photos.count)+" fotos" : "1 foto"
			}
		}catch let error {
			print("error: "+error.localizedDescription);
		}
    }

    func changeEnabledButton() {
        self.captureButton.isEnabled = false
    }
}
