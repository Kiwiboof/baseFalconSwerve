// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.can.TalonFX;
import com.ctre.phoenix.sensors.CANCoder;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMaxLowLevel;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxPIDController;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.util.Units;

import edu.wpi.first.wpilibj.RobotBase;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.utilities.RevUtils;
import frc.robot.utilities.SwerveModuleConstants;

import static frc.robot.Constants.*;

public class SwerveModule extends SubsystemBase {
  private final int POS_SLOT = 0;
  private final int VEL_SLOT = 0;

  public static final double kDriveRevToMeters = ((WHEEL_DIAMETER * Math.PI) / DRIVE_GEAR_RATIO);
  public static final double kDriveRpmToMetersPerSecond = kDriveRevToMeters / 60.0;
  public static final double kTurnRotationsToDegrees = 360.0 / TURN_GEAR_RATIO;

  private TalonFX m_driveMotor;
  private TalonFX m_turningMotor;
  private CANCoder m_angleEncoder;

  public final RelativeEncoder m_driveEncoder;
  private final RelativeEncoder m_turnEncoder;

  private double m_angleOffset;

  private final SparkMaxPIDController m_driveController;
  private SparkMaxPIDController m_turnController;

  double m_currentAngle;
  double m_lastAngle;

  private int m_moduleNumber;

  /**
   * Constructs a SwerveModule.
   *
   * @param moduleNumber The module number
   * @param swerveModuleConstants     Swerve modules constants to setup swerve module
   */
  public SwerveModule(int moduleNumber, SwerveModuleConstants swerveModuleConstants) {
    m_moduleNumber = moduleNumber;

    m_driveMotor = new TalonFX(swerveModuleConstants.driveMotorChannel);
    m_turningMotor = new TalonFX(swerveModuleConstants.turningMotorChannel);

    m_angleEncoder = new CANCoder(swerveModuleConstants.cancoderID, "rio");
    m_angleOffset = swerveModuleConstants.angleOffset;

    m_driveMotor.configFactoryDefault();
    m_driveMotor.getPIDConfigs(null);
    RevUtils.setDriveMotorConfig(m_driveMotor, false);
    m_driveMotor.setIdleMode(CANSparkMax.IdleMode.kBrake);
    m_driveMotor.setInverted(true);// MK4i drive motor is inverted

    m_turningMotor.restoreFactoryDefaults();
    RevUtils.setTurnMotorConfig(m_turningMotor, false);
    m_turningMotor.setIdleMode(CANSparkMax.IdleMode.kBrake);

    m_turningMotor.setSmartCurrentLimit(25);
    m_turningMotor.enableVoltageCompensation(12.6);
    m_turningMotor.setInverted(true); // MK4i Steer Motor is inverted

    m_angleEncoder.configFactoryDefault();

    m_driveEncoder = m_driveMotor.getEncoder();
    m_driveEncoder.setPositionConversionFactor(kDriveRevToMeters);
    m_driveEncoder.setVelocityConversionFactor(kDriveRpmToMetersPerSecond);
    m_driveEncoder.setPosition(0);

    m_turnEncoder = m_turningMotor.getEncoder();
    m_turnEncoder.setPositionConversionFactor(kTurnRotationsToDegrees);
    m_turnEncoder.setVelocityConversionFactor(kTurnRotationsToDegrees / 60);

    m_driveController = m_driveMotor.getPIDController();
    m_turnController = m_turningMotor.getPIDController();
  }

  /**
   * Returns the current state of the module.
   *
   * @return The current state of the module.
   */
  public SwerveModuleState getState() {
    return new SwerveModuleState(getDriveMetersPerSecond(), getHeadingRotation2d());
  }

  /**
   * Returns the current position of the module.
   *
   * @return The current position of the module.
   */
  public SwerveModulePosition getPosition() {
    return new SwerveModulePosition(getDriveMeters(), getHeadingRotation2d());
  }

  public void resetAngleToAbsolute() {
    double angle = m_angleEncoder.getAbsolutePosition() - m_angleOffset;
    m_turnEncoder.setPosition(angle);
  }

  public double getHeadingDegrees() {
    if (RobotBase.isReal())
      return m_turnEncoder.getPosition();
    else
      return m_currentAngle;
  }

  public Rotation2d getHeadingRotation2d() {
    return Rotation2d.fromDegrees(getHeadingDegrees());
  }

  public double getDriveMeters() {
    return m_driveEncoder.getPosition();
  }

  public double getDriveMetersPerSecond() {
    return m_driveEncoder.getVelocity();
  }

  public SwerveDriveKinematics getSwerveKinematics() {
    return SwerveDrive.kDriveKinematics;
  }

  public void setDesiredState(SwerveModuleState desiredState, boolean isOpenLoop) {
    desiredState = RevUtils.optimize(desiredState, getHeadingRotation2d());

    if (isOpenLoop) {
      double percentOutput = desiredState.speedMetersPerSecond / MAX_METERS_PER_SECOND;
      m_driveMotor.set(percentOutput);
    } else {
      int DRIVE_PID_SLOT = VEL_SLOT;
      m_driveController.setReference(
          desiredState.speedMetersPerSecond,
          CANSparkMax.ControlType.kVelocity,
          DRIVE_PID_SLOT);
    }

    double angle = (Math.abs(desiredState.speedMetersPerSecond) <= (MAX_METERS_PER_SECOND * 0.01)) // Prevent rotating module if speed is less than 1%. Prevents Jittering.
        ? m_lastAngle
        : desiredState.angle.getDegrees(); 
    angle = desiredState.angle.getDegrees();
    m_turnController.setReference(angle, CANSparkMax.ControlType.kPosition, POS_SLOT);
    m_lastAngle = angle;

    SmartDashboard.putNumber(m_moduleNumber + " Speed", Units.metersToFeet(desiredState.speedMetersPerSecond));
    SmartDashboard.putNumber(m_moduleNumber + " Angle", angle);
  }
}
