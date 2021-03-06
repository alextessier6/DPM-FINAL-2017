package finalProject;
import lejos.hardware.Sound;
import lejos.hardware.ev3.LocalEV3;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.SampleProvider;

/**
 * The navigator is used to change either the robot's position 
 * or heading in a controlled manner. The navigator intakes values 
 * from the odometer continuously and should first move the robot towards its
 * target position or target heading. During these movements the robot should
 * consistently be the calling correction and obstacle avoidance methods
 * to ensure the robot reaches the correct final position.
 * 
 * 
 * @author Ian Gauthier
 * @author Ilana Haddad
 * @author Tristan Bouchard
 * @author Tyrone Wong
 * @author Alexandre Tessier
 * 
 * @version 2.0
 *
 */

public class Navigation{

	double wheel_radius = WiFiExample.WHEEL_RADIUS;
	double width =  WiFiExample.TRACK;
	private static final int FORWARD_SPEED = WiFiExample.FORWARD_SPEED;
	private static final int ROTATE_SPEED = WiFiExample.ROTATE_SPEED;
	public double return_theta;
	/**The odometer value of the X position */
	public double odo_x;
	/** The odometer value of the Y position of the robot. */
	public double odo_y;
	/** The odometer value of the angle of the robot. */
	public double odo_theta;

	/** The X coordinate of the destination point. */
	public double x_dest;
	/** The Y coordinate of the destination point. */
	public double y_dest;
	/** The desired heading of the robot. */
	public double theta_dest;

	/** The left wheel's motor. Initialized in main class WiFiExample and passed on in Navigation.*/
	private EV3LargeRegulatedMotor leftMotor = WiFiExample.leftMotor;
	/** The right wheel's motor. Initialized in main class WiFiExample and passed on in Navigation.*/
	private EV3LargeRegulatedMotor rightMotor = WiFiExample.rightMotor;

	/** Meant to store the value of the R and L light sensors to determine if a black line is detected*/
	private float[] correctionLine;

	/** Boolean to store whether the robot is turning. Initialized to false. */
	public static boolean turning=false; 

	public boolean localizing=false;
	public boolean stop = false;
	public static boolean finishTravel = false;

	/**The Odometer of the robot */
	public Odometer odometer = WiFiExample.odometer;


	/**
	 * Constructor for Navigation
	 * @param odometer the odometer of the robot
	 */

	public Navigation(Odometer odometer){ //constructor
		this.odometer = odometer;
	}

	/**
	 * The method moves the robot to the position that is inputed into the 
	 * method by first traveling to (x,0) and then to (x,y), breaking it up into two steps.
	 * Therefore, this method travels in straight lines rather than diagonally to final coordinates.
	 * 
	 * @param x the X coordinate that should be moved to
	 * @param y the X coordinate that should be moved to
	 */
	public void travelTo(double x_dest, double y_dest){
		//this method causes robot to travel to the absolute field location (x,y)

		if(stop){
			return;
		}
		this.x_dest = x_dest;
		this.y_dest = y_dest;
		odo_x = odometer.getX();
		odo_y = odometer.getY();
		odo_theta = odometer.getAng();

		//calculate the distance we want the robot to travel in x and y 
		double delta_y = y_dest-odo_y;
		double delta_x = x_dest-odo_x;

		drive(delta_x,delta_y,x_dest,y_dest);
	}

	/**
	 * This method will travel to the coordinates x and y diagonally rather than split into x and y.
	 * This should call the turnTo method to turn to the correct heading 
	 * of the robot before finding the distance and moving that distance in 
	 * a straight heading.
	 * @param x the X coordinate that should be moved to
	 * @param y the X coordinate that should be moved to
	 */
	public void travelToDiag(double x, double y){
		//this method causes robot to travel to the absolute field location (x,y)

		if(stop){
			return;
		}

		x_dest = x;
		y_dest = y;

		odo_x = odometer.getX();
		odo_y = odometer.getY();
		odo_theta = odometer.getAng();
		x_dest = x;
		y_dest = y;

		//calculate the distance we want the robot to travel in x and y 
		double delta_y = y_dest-odo_y;
		double delta_x = x_dest-odo_x;

		//calculate desired theta heading: theta = arctan(y/x)

		//theta_dest = Math.toDegrees(Math.atan2(delta_x,delta_y));

		//distance to travel: d = sqrt(x^2+y^2)
		double travelDist = Math.hypot(delta_x,delta_y);
		//Math.hypot calculates the hypotenuse of its arguments (distance we want to find)

		//subtract odo_theta from theta_dest:
		double theta_corr = (theta_dest - odo_theta);

		//DIRECTING ROBOT TO CORRECT ANGLE: 
		if(theta_corr < -180){ //if theta_dest is between angles [-180,-360] 
			//add 360 degrees to theta_dest in order for the robot to turn the smallest angle
			turnTo(theta_corr + 360);
		}
		else if(theta_corr > 180){ //if theta_dest is between angles [180,360]
			//subtract 360 degrees from theta_dest in order for the robot to turn the smallest angle
			turnTo(theta_corr - 360);
		}
		else{
			turnTo(theta_corr);
		}

		driveDiag(travelDist);
	}


	/**
	 * This method should move the robot to by the given amount first in the x dimension
	 * and then in the y dimension all while continually calling the obstacle avoidance 
	 * and correction classes to ensure that the robot is moving to the correct point.
	 * The grid line correction should be performed when the robot reaches each grid line
	 * and the localization correction should be performed after the robot has crossed over 
	 * six grid lines since its last localization.
	 * 
	 * @param delta_x the difference in x position from start to finish
	 * @param delta_y the difference in y position from start to finish
	 * @param x_dest the final x position
	 * @param y_dest the final y position
	 */
	public void drive(double delta_x,double delta_y,double x_dest,double y_dest){

		synchronized(leftMotor){
			synchronized(rightMotor){
				//set both motors to forward speed desired
				leftMotor.setSpeed(FORWARD_SPEED);
				rightMotor.setSpeed(FORWARD_SPEED);
				leftMotor.setAcceleration(1000);
				rightMotor.setAcceleration(1000);

				//X-travel
				if(Math.abs(delta_x)<1){
					delta_x=0;
				}	
				if(Math.abs(delta_x)>1){
					if(delta_x>1)
						turnToSmart(90);
					else{
						turnToSmart(270);
					}
				}

				leftMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_x)), true);
				rightMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_x)), true);

				//might need to add a travel to after while loop to make sure it's in the right location
				while(leftMotor.isMoving()&&rightMotor.isMoving()){
					WiFiExample.correction.LightCorrection();
					if(WiFiExample.cont.avoidingOb = true){
						return_theta = odometer.getAng();
						avoidOb(x_dest,y_dest);
					}
					if(WiFiExample.correction.gridcount==5){
						localize();
					}

				}
				if(finishTravel){
					return;
				}

				//Y-travel
				if(Math.abs(delta_y)<1){
					delta_y=0;
				}	
				if(Math.abs(delta_y)>1){
					if(delta_y>1)
						turnToSmart(0);
					else{
						turnToSmart(180);
					}
				}

				leftMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_y)), true);
				rightMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_y)), true);

				//might need to add a travel to after while loop to make sure it's in the right location
				while(leftMotor.isMoving()&&rightMotor.isMoving()){
					WiFiExample.correction.LightCorrection();
					if(WiFiExample.cont.avoidingOb = true){
						return_theta = odometer.getAng();
						avoidOb(x_dest,y_dest);
					}
					if(WiFiExample.correction.gridcount==5){
						localize();
						return;
					}
				}
			}
		}
	}


	//this method activates obstacle avoidance and continues travel after the avoidance is done
	/**
	 * This method should call the obstacle avoidance system when there has been a block
	 * detected, avoid the obstacle and then travel to the position that was initially
	 * the goal.
	 *	
	 * @param x_dest the x coordinate of the final position
	 * @param y_dest the y coordinate of the final position
	 */
	public void avoidOb(double x_dest,double y_dest){
		WiFiExample.cont.avoidOB();
		if(WiFiExample.cont.avoidedBlock){
			WiFiExample.correction.localizeFWD();
			travelTo(x_dest,y_dest);
			finishTravel = true;
		}
	}

	/**
	 * This method travels to distance inputed diagonally.
	 * @param travelDist
	 */
	public void driveDiag(double travelDist){

		synchronized(leftMotor){
			synchronized(rightMotor){

				//set both motors to forward speed desired
				leftMotor.setSpeed(FORWARD_SPEED);
				rightMotor.setSpeed(FORWARD_SPEED);
				leftMotor.setAcceleration(1000);
				rightMotor.setAcceleration(1000);
				
				leftMotor.rotate(convertDistance(wheel_radius, travelDist), true);
				rightMotor.rotate(convertDistance(wheel_radius, travelDist), true);

				leftMotor.waitComplete();
				rightMotor.waitComplete();

			}
		}
	}

	/**
	 * This method is very similar to the drive method except
	 * that it should continuously call the corrected method for grid line
	 * correction to fix the heading of the robot as it moves to the final
	 * position.
	 * 
	 * @param travelDist the distance to be traveled
	 */
	public void driveWCorrection(double travelDist){
		synchronized(leftMotor){
			synchronized(rightMotor){

				//set both motors to forward speed desired
				leftMotor.setSpeed(FORWARD_SPEED);
				rightMotor.setSpeed(FORWARD_SPEED);
				leftMotor.setAcceleration(1000);
				rightMotor.setAcceleration(1000);

				leftMotor.rotate(convertDistance(wheel_radius, travelDist), true);
				rightMotor.rotate(convertDistance(wheel_radius, travelDist), true);
	
				while(leftMotor.isMoving()&&rightMotor.isMoving()){ //&&
					WiFiExample.correction.LightCorrection();

				}
				WiFiExample.correction.gridcount = 0;

				motorstop();

			}
		}
	}
	/**
	 * The method should intake an angle and then turn the robot to that angle.
	 * 
	 * @param theta the angle which should be turned to
	 */
	public void turnTo(double theta){
		synchronized(leftMotor){
			synchronized(rightMotor){

				if(stop){
					return;
				}

				turning = true;
				Sound.twoBeeps(); //DONT REMOVE THIS
				//make robot turn to angle theta:
				leftMotor.setSpeed(ROTATE_SPEED);
				leftMotor.setAcceleration(1000);
				rightMotor.setSpeed(ROTATE_SPEED);
				rightMotor.setAcceleration(1000);

				leftMotor.rotate(convertAngle(wheel_radius, width, theta), true);
				rightMotor.rotate(-convertAngle(wheel_radius, width, theta), false);

				//returns default acceleration values after turn
				leftMotor.setAcceleration(1000);
				rightMotor.setAcceleration(1000);
				leftMotor.setSpeed(FORWARD_SPEED);
				rightMotor.setSpeed(FORWARD_SPEED);
				Sound.twoBeeps();
			}
		}

		turning = false;
	}

	/**
	 * This method should call the correction class' localization method
	 * and then when finished should call the robot to travel to the initial
	 * destination of the robot.
	 */
	public void localize(){
		WiFiExample.correction.localize();
		travelTo(x_dest,y_dest);
		finishTravel = true;
	}


	/**
	 * The method should convert the input distance into a form that is equal to
	 * the amount of rotation that a wheel of the given radius must rotate
	 * in order to move that distance
	 * 
	 * @param radius the radius of the wheels of the robot
	 * @param distance the distance which will be converted
	 * @return the converted distance
	 */
	private static int convertDistance(double radius, double distance) {
		return (int) ((180.0 * distance) / (Math.PI * radius));
	}

	/**
	 * The method should convert the input angle into a form that can be performed
	 * by the robot with the given wheel radius and width.
	 * 
	 * 
	 * @param radius the radius of the wheel
	 * @param width the width of the robot
	 * @param angle the angle to be converted
	 * @return the angle now in the form of amount of rotation needed by the robot's wheel to perform that angle of turn
	 */
	private static int convertAngle(double radius, double width, double angle) {
		return convertDistance(radius, Math.PI * width * angle / 360.0);
	}

	/**
	 * The method causes the robot to turn to an angle in relation to its current
	 * heading meaning that if 150 is input, the robot should turn 150 degrees
	 * 
	 * @param angle the amount to be turned
	 */
	public void turnToSmart(double angle){
		if(stop){
			return;
		}
		odo_theta = odometer.getAng();

		//subtract odo_theta from theta_dest:
		double theta_corr = angle - odo_theta;
		//DIRECTING ROBOT TO CORRECT ANGLE: 
		if(theta_corr < -180){ //if theta_dest is between angles [-180,-360] 
			//add 360 degrees to theta_dest in order for the robot to turn the smallest angle
			turnTo(theta_corr + 360);
		}
		else if(theta_corr > 180){ //if theta_dest is between angles [180,360]
			//subtract 360 degrees from theta_dest in order for the robot to turn the smallest angle
			turnTo(theta_corr - 360);
		}
		else{
			turnTo(theta_corr);
		}

	}

	/**
	 * 
	 * @return the coordinates of the destination of the robot
	 */
	public double[] getDest(){
		double[] coordinates = {0.0,0.0,0.0};
		coordinates[0]=x_dest;
		coordinates[1]=y_dest;
		coordinates[2]=theta_dest;

		return coordinates;
	}

	/**
	 * 
	 * @return a boolean that is true if the robot it turning and false if it not
	 */
	public boolean isTurning(){
		return turning; 
	}

	/**
	 * This method is used to very quickly stop the robot from moving in whatever direction it is
	 * moving in before setting it's speed to be 150 degrees/second shortly afterward.
	 */
	public void motorstop(){
		leftMotor.setAcceleration(10000);
		rightMotor.setAcceleration(10000);
		leftMotor.setSpeed(0);
		rightMotor.setSpeed(0);
		leftMotor.forward();
		rightMotor.forward();
		leftMotor.stop(true);
		rightMotor.stop(false);
		leftMotor.setSpeed(ROTATE_SPEED);
		rightMotor.setSpeed(ROTATE_SPEED);
		leftMotor.setAcceleration(1000);
		rightMotor.setAcceleration(1000);
	}

	/**
	 * This method should return an exception when the robot's navigation has been interrupted
	 * to alert the system of the interruption.
	 */
	public void stopNav(){

		while(stop==true){
			try {
				localizing=WiFiExample.correction.islocalizing();
				Thread.sleep(500);
			} catch (InterruptedException e) {}
		}
	}

	/**
	 * This method is identical to the original travelTo method except that it
	 * moves in the y dimension before it moves in the x dimension.
	 * 
	 * @param x_dest the x coordinate of the final position
	 * @param y_dest the y coordinate of the final position
	 */
	public void travelToYFIRST(double x_dest, double y_dest){
		//this method causes robot to travel to the absolute field location (x,y)

		if(stop){
			return;
		}
		this.x_dest = x_dest;
		this.y_dest = y_dest;
		odo_x = odometer.getX();
		odo_y = odometer.getY();
		odo_theta = odometer.getAng();

		//calculate the distance we want the robot to travel in x and y 
		double delta_y = y_dest-odo_y;
		double delta_x = x_dest-odo_x;

		driveYFIRST(delta_x,delta_y,x_dest,y_dest);
	}

	/**
	 * This method is very similar to the original drive method except that it
	 * drives in the y dimension before driving in the x dimension.
	 * 
	 * @param delta_x the difference in x position from start to finish
	 * @param delta_y the difference in y position from start to finish
	 * @param x_dest the final x position
	 * @param y_dest the final y position
	 */
	public void driveYFIRST(double delta_x,double delta_y,double x_dest,double y_dest){

		synchronized(leftMotor){
			synchronized(rightMotor){
				//set both motors to forward speed desired
				leftMotor.setSpeed(FORWARD_SPEED);
				rightMotor.setSpeed(FORWARD_SPEED);
				leftMotor.setAcceleration(1000);
				rightMotor.setAcceleration(1000);

				//Y-travel
				if(Math.abs(delta_y)<1){
					delta_y=0;
				}	
				if(Math.abs(delta_y)>1){
					if(delta_y>1)
						turnToSmart(0);
					else{
						turnToSmart(180);
					}
				}

				leftMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_y)), true);
				rightMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_y)), true);
				
				//might need to add a travel to after while loop to make sure it's in the right location
				while(leftMotor.isMoving()&&rightMotor.isMoving()){
					WiFiExample.correction.LightCorrection();

					if(WiFiExample.cont.avoidingOb = true){
						return_theta = odometer.getAng();
						avoidOb(x_dest,y_dest);		
					}
					if(WiFiExample.correction.gridcount==5){
						localize();
						return;
					}
				}
				if(finishTravel){
					return;
				}
				//X-travel
				if(Math.abs(delta_x)<1){
					delta_x=0;
				}	
				if(Math.abs(delta_x)>1){
					if(delta_x>1)
						turnToSmart(90);
					else{
						turnToSmart(270);
					}
				}

				leftMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_x)), true);
				rightMotor.rotate(convertDistance(wheel_radius, Math.abs(delta_x)), true);
				
				//might need to add a travel to after while loop to make sure it's in the right location
				while(leftMotor.isMoving()&&rightMotor.isMoving()){
					WiFiExample.correction.LightCorrection();
					if(WiFiExample.cont.avoidingOb = true){
						return_theta = odometer.getAng();
						avoidOb(x_dest,y_dest);
					}
					if(WiFiExample.correction.gridcount==5){
						localize();
					}
				}
			}
		}
	}

}

