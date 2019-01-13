package com.hemanthraj.fluttercompass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.PluginRegistry.Registrar

class FlutterCompassPlugin private constructor(context: Context, sensorType: Int) : EventChannel.StreamHandler {
  private var mAzimuth = 0.0 // degree
  private var newAzimuth = 0.0 // degree
  private var mFilter = 1f
  private var sensorEventListener: SensorEventListener? = null
  private val sensorManager: SensorManager
  private val sensor: Sensor
  private val orientation = FloatArray(3)
  private val rMat = FloatArray(9)

  private var mRotationV: Sensor
  private var mAccelerometer:Sensor
  private var mMagnetometer:Sensor

  private var haveSensor = false
  private var haveSensor2 = false
  private var mLastAccelerometerSet = false
  private var mLastMagnetometerSet = false

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar): Unit {
      //1. in here, we'll figure out what streams are available, and set our compass plugin with that information.. likely having 'static' properties in here
      val channel = EventChannel(registrar.messenger(), "hemanthraj/flutter_compass")
      channel.setStreamHandler(FlutterCompassPlugin(registrar.context(), Sensor.TYPE_ROTATION_VECTOR))
    }
  }

  init {
    //this line corresponds to the java app line 35
    //java -> mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    //2. We'll listen to the event streams determined from 1. here... probably. Not sure
    sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    //sensor = sensorManager.getDefaultSensor(sensorType) //commented out
    start()
  }

  public fun start() {
    if(sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
      if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null || sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) == null) {
        noSensorsAlert()
      } else {
        mAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mMagnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        haveSensor = sensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI)
        haveSensor2 = sensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_UI)
      }
    }
    else {
      mRotationV = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
      haveSensor = sensorManager.registerListener(this, mRotationV, SensorManager.SENSOR_DELAY_UI)
    }
  }

  fun stop() {
    if (haveSensor) {
      sensorManager.unregisterListener(this, mRotationV)
    } else {
      sensorManager.unregisterListener(this, mAccelerometer)
      sensorManager.unregisterListener(this, mMagnetometer)
    }
  }

  override fun onListen(arguments: Any?, events: EventChannel.EventSink) {

    sensorEventListener = createSensorEventListener(events)
    sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
  }

  override fun onCancel(arguments: Any?) {
    sensorManager.unregisterListener(sensorEventListener)
  }

  internal fun createSensorEventListener(events: EventChannel.EventSink): SensorEventListener {
    return object : SensorEventListener {
      override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

      override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
          SensorManager.getRotationMatrixFromVector(rMat, event.values)
          newAzimuth = (Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0].toDouble()) + 360).toInt() % 360
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
          System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.size)
          mLastAccelerometerSet = true
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
          System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.size)
          mLastMagnetometerSet = true
        }

        if (mLastAccelerometerSet && mLastMagnetometerSet) {
          SensorManager.getRotationMatrix(rMat, null, mLastAccelerometer, mLastMagnetometer)
          SensorManager.getOrientation(rMat, orientation)
          newAzimuth = (Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0].toDouble()) + 360).toInt() % 360
        }

        // calculate th rotation matrix
        //SensorManager.getRotationMatrixFromVector(rMat, event.values)
        // get the azimuth value (orientation[0]) in degree

        //newAzimuth = (((Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[0].toDouble()) + 360) % 360 - Math.toDegrees(SensorManager.getOrientation(rMat, orientation)[2].toDouble()) + 360) % 360)

        //dont react to changes smaller than the filter value
        if (Math.abs(mAzimuth - newAzimuth) < mFilter) {
          return
        }
        mAzimuth = newAzimuth

        events.success(mAzimuth);
      }
    }
  }
}

