import Foundation;

protocol CameraButtonDelegate {
    func takePicture()
    func changeEnabledButton()
}

class CaptureButton: UIButton {
    var delegate: CameraButtonDelegate?
    
    override init(frame: CGRect) {
        super.init(frame: frame);
		/*
		self.center = CGPoint(x: frame.width / 1.12, y: frame.height / (3/4));
        self.frame.size = CGSize(width: 80, height: 80);
        self.backgroundColor = UIColor.white;
        self.layer.cornerRadius = self.frame.width / 2;
		*/
        self.setup();
        self.addTarget(self, action: #selector(tappedButton(_:)), for: .touchUpInside);
    }
    
    func buttonEnabled() {
        delegate?.changeEnabledButton();
    }

	func setup(){
        let screenSize = UIScreen.main.bounds;
        let screenWidth = screenSize.width;
        let screenHeight = screenSize.height;
        print("CaptureButton setup()");
        print("screenSize: ",screenWidth,"x",screenHeight);
        print("CaptureButton setup() --> Frame width: ",frame.width);
        print("CaptureButton setup() --> Frame height: ",frame.height);
//        self.center = CGPoint(x: frame.width/* / 1.12*/, y: frame.height/* / (3/4)*/);
        self.frame.size = CGSize(width: 64, height: 64);
//        self.center = CGPoint(x: self.frame.size.width/2, y: self.frame.size.height/2);
        self.backgroundColor = UIColor.white;
        self.layer.cornerRadius = self.frame.width / 2;
	}
    
    @objc func tappedButton(_ sender: UIButton) {
        delegate?.takePicture();
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented");
    }
    
}
