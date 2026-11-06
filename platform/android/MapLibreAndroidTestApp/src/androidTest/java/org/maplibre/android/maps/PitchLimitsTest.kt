package org.maplibre.android.maps

import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.constants.MapLibreConstants
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.testapp.activity.BaseTest
import org.maplibre.android.testapp.activity.maplayout.SimpleMapActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PitchLimitsTest : BaseTest() {
    override fun getActivityClass() = SimpleMapActivity::class.java

    private fun tiltTo(tilt: Double) {
        val position = CameraPosition.Builder().target(LatLng(12.0, 12.0)).zoom(4.0).tilt(tilt).build()
        maplibreMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
    }

    @Test
    fun maxPitch_aboveDefault_isAccepted() {
        validateTestSetup()
        rule.runOnUiThread {
            assertEquals(MapLibreConstants.DEFAULT_MAXIMUM_PITCH.toDouble(), maplibreMap.maxPitch, DELTA)

            maplibreMap.setMaxPitchPreference(90.0)
            assertEquals(90.0, maplibreMap.maxPitch, DELTA)

            maplibreMap.setMaxPitchPreference(MapLibreConstants.MAXIMUM_PITCH.toDouble())
            assertEquals(180.0, maplibreMap.maxPitch, DELTA)
        }
    }

    @Test
    fun maxPitch_outOfRange_isIgnored() {
        validateTestSetup()
        rule.runOnUiThread {
            maplibreMap.setMaxPitchPreference(90.0)
            maplibreMap.setMaxPitchPreference(180.5)
            maplibreMap.setMaxPitchPreference(-1.0)
            assertEquals(90.0, maplibreMap.maxPitch, DELTA)
        }
    }

    @Test
    fun minPitch_aboveDefaultMax_isAcceptedOnceMaxIsRaised() {
        validateTestSetup()
        rule.runOnUiThread {
            maplibreMap.setMaxPitchPreference(120.0)
            maplibreMap.setMinPitchPreference(90.0)
            assertEquals(90.0, maplibreMap.minPitch, DELTA)
            assertEquals(120.0, maplibreMap.maxPitch, DELTA)
        }
    }

    @Test
    fun cameraTilt_isClampedToRuntimeMaxPitch() {
        validateTestSetup()
        rule.runOnUiThread {
            tiltTo(90.0)
            assertEquals(60.0, maplibreMap.cameraPosition.tilt, DELTA)

            maplibreMap.setMaxPitchPreference(120.0)
            tiltTo(100.0)
            assertEquals(100.0, maplibreMap.cameraPosition.tilt, DELTA)

            tiltTo(150.0)
            assertEquals(120.0, maplibreMap.cameraPosition.tilt, DELTA)
        }
    }

    @Test
    fun mapOptions_minPitchAboveDefaultMax_isApplied() {
        validateTestSetup()
        val ready = CountDownLatch(1)
        var optionsMapView: MapView? = null
        var minPitch = Double.NaN
        var maxPitch = Double.NaN

        rule.runOnUiThread {
            val options = MapLibreMapOptions.createFromAttributes(context)
                .minPitchPreference(90.0)
                .maxPitchPreference(120.0)
            val newMapView = MapView(context, options)
            optionsMapView = newMapView
            (mapView.parent as ViewGroup).addView(
                newMapView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            newMapView.onCreate(null)
            newMapView.onStart()
            newMapView.onResume()
            newMapView.getMapAsync { map ->
                minPitch = map.minPitch
                maxPitch = map.maxPitch
                ready.countDown()
            }
        }

        val mapReady = ready.await(30, TimeUnit.SECONDS)
        rule.runOnUiThread {
            optionsMapView?.let {
                it.onPause()
                it.onStop()
                it.onDestroy()
                (it.parent as ViewGroup).removeView(it)
            }
        }

        assertTrue("Map created from options did not become ready", mapReady)
        assertEquals(90.0, minPitch, DELTA)
        assertEquals(120.0, maxPitch, DELTA)
    }

    companion object {
        private const val DELTA = 0.01
    }
}
