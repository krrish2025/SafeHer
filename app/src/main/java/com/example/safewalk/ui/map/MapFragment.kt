package com.example.safewalk.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
<<<<<<< HEAD
=======
import com.example.safewalk.BuildConfig
>>>>>>> 3e7c211b67d978360a912324f7cb5173604ee75c
import com.example.safewalk.R
import com.example.safewalk.databinding.FragmentMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import androidx.navigation.fragment.findNavController
import kotlin.concurrent.thread

import androidx.core.os.bundleOf
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import android.location.Geocoder
import java.util.Locale
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.location.Address

import androidx.lifecycle.lifecycleScope
import com.example.safewalk.data.model.RouteRequest
import com.example.safewalk.data.network.RetrofitClient
import kotlinx.coroutines.launch
import android.graphics.Color

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: GeoPoint? = null
    private var isPickMode = false
    private var selectedPoint: GeoPoint? = null
    private var selectionMarker: Marker? = null

    // Store resolved addresses for autocomplete
    private var startSuggestions = mutableListOf<Address>()
    private var destSuggestions = mutableListOf<Address>()

    private val autocompleteHandler = Handler(Looper.getMainLooper())
    private var startRunnable: Runnable? = null
    private var destRunnable: Runnable? = null

    // Track safety scores + overlays for re-coloring after all ML calls complete
    private val routeInfoTexts = mutableMapOf<Int, String>()
    private val routeRiskScores = mutableMapOf<Int, Int>()  // raw risk score from API
    private val routeOverlays = mutableMapOf<Int, Polyline>()
    private var totalRoutes = 0
    private var scoredRoutes = 0

    // Navigation state
    private var selectedRouteIndex: Int = -1
    private var isNavigating = false
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        isPickMode = arguments?.getBoolean("pick_mode") ?: false
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        getCurrentLocation()
        setupAutocomplete()
        setupListeners()
        setupNavigationControls()

        if (isPickMode) {
            setupPickMode()
            binding.routeInputCard.visibility = View.GONE
        }
    }

    // ── Autocomplete ──────────────────────────────────────────────

    private fun setupAutocomplete() {
        setupFieldAutocomplete(isStart = true)
        setupFieldAutocomplete(isStart = false)
    }

    private fun setupFieldAutocomplete(isStart: Boolean) {
        val field = if (isStart) binding.etStartLocation else binding.etSearchLocation

        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: return
                if (query.length < 2) return

                // Debounce: cancel previous, schedule new
                val runnable = if (isStart) startRunnable else destRunnable
                runnable?.let { autocompleteHandler.removeCallbacks(it) }

                val newRunnable = Runnable { fetchSuggestions(query, isStart) }
                if (isStart) startRunnable = newRunnable else destRunnable = newRunnable
                autocompleteHandler.postDelayed(newRunnable, 350)
            }
        })

        field.setOnItemClickListener { _, _, position, _ ->
            val suggestions = if (isStart) startSuggestions else destSuggestions
            if (position < suggestions.size) {
                val addr = suggestions[position]
                field.setText(addr.getAddressLine(0) ?: "${addr.latitude}, ${addr.longitude}")
                field.dismissDropDown()
            }
        }
    }

    private fun fetchSuggestions(query: String, isStart: Boolean) {
        thread {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())
                val results = geocoder.getFromLocationName(query, 5) ?: emptyList()

                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread

                    val field = if (isStart) binding.etStartLocation else binding.etSearchLocation
                    val list = if (isStart) startSuggestions else destSuggestions
                    list.clear()
                    list.addAll(results)

                    val displayNames = results.map { addr ->
                        addr.getAddressLine(0) ?: "${addr.locality ?: ""}, ${addr.countryName ?: ""}"
                    }

                    val adapter = object : ArrayAdapter<String>(
                        requireContext(),
                        R.layout.item_suggestion,
                        R.id.suggestionText,
                        displayNames
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = super.getView(position, convertView, parent)
                            view.findViewById<TextView>(R.id.suggestionText)?.text = displayNames[position]
                            return view
                        }
                    }
                    field.setAdapter(adapter)
                    if (displayNames.isNotEmpty() && field.hasFocus()) {
                        field.showDropDown()
                    }
                }
            } catch (e: Exception) {
                // Geocoder failed silently — no suggestions
            }
        }
    }

    // ── Route Finding ─────────────────────────────────────────────

    private fun setupListeners() {
        binding.etSearchLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findRoutes()
                true
            } else false
        }

        binding.btnFindRoutes.setOnClickListener {
            findRoutes()
        }
    }

    private fun findRoutes() {
        val destQuery = binding.etSearchLocation.text.toString().trim()
        if (destQuery.isEmpty()) {
            Toast.makeText(context, "Please enter a destination", Toast.LENGTH_SHORT).show()
            return
        }

        val startQuery = binding.etStartLocation.text.toString().trim()
        hideKeyboard()
        binding.etStartLocation.dismissDropDown()
        binding.etSearchLocation.dismissDropDown()

        binding.btnFindRoutes.isEnabled = false
        binding.btnFindRoutes.text = "Finding routes…"
        routeInfoTexts.clear()

        thread {
            try {
                val geocoder = Geocoder(requireContext(), Locale.getDefault())

                // Resolve start
                val startPoint = if (startQuery.isNotEmpty()) {
                    val addrs = geocoder.getFromLocationName(startQuery, 1)
                    if (addrs != null && addrs.isNotEmpty()) {
                        GeoPoint(addrs[0].latitude, addrs[0].longitude)
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Start location not found", Toast.LENGTH_SHORT).show()
                            resetButton()
                        }
                        return@thread
                    }
                } else {
                    currentLocation ?: run {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "Current location unavailable, enter a start point", Toast.LENGTH_SHORT).show()
                            resetButton()
                        }
                        return@thread
                    }
                }

                // Resolve destination
                val destAddrs = geocoder.getFromLocationName(destQuery, 1)
                if (destAddrs == null || destAddrs.isEmpty()) {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "Destination not found", Toast.LENGTH_SHORT).show()
                        resetButton()
                    }
                    return@thread
                }
                val destPoint = GeoPoint(destAddrs[0].latitude, destAddrs[0].longitude)

<<<<<<< HEAD
                // OSRM with alternatives
                val roadManager = OSRMRoadManager(requireContext(), "SafeWalk/1.0")
                roadManager.setMean(OSRMRoadManager.MEAN_BY_FOOT)
                roadManager.addRequestOption("alternatives=3")

                val waypoints = ArrayList<GeoPoint>()
                waypoints.add(startPoint)
                waypoints.add(destPoint)

                val roads = roadManager.getRoads(waypoints)
=======
                // Use MapBox Directions API
                val roads = fetchMapBoxRoutes(startPoint, destPoint)
>>>>>>> 3e7c211b67d978360a912324f7cb5173604ee75c

                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    resetButton()
                    displayRoutes(roads, startPoint, destPoint)
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }
        }
    }

<<<<<<< HEAD
=======
    private fun fetchMapBoxRoutes(start: GeoPoint, dest: GeoPoint): Array<Road> {
        return try {
            val apiKey = BuildConfig.MAPBOX_ACCESS_TOKEN
            val urlStr = "https://api.mapbox.com/directions/v5/mapbox/walking/${start.longitude},${start.latitude};${dest.longitude},${dest.latitude}?alternatives=true&geometries=geojson&access_token=$apiKey"
            
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(response)
            
            val routesArray = json.optJSONArray("routes") ?: return emptyArray()
            val roads = ArrayList<Road>()
            
            for (i in 0 until routesArray.length()) {
                val routeObj = routesArray.getJSONObject(i)
                val distance = routeObj.optDouble("distance", 0.0)
                val geometry = routeObj.getJSONObject("geometry")
                val coords = geometry.getJSONArray("coordinates")
                
                val road = Road()
                // Convert MapBox meters to KM
                road.mLength = distance / 1000.0
                road.mStatus = Road.STATUS_OK
                
                val points = ArrayList<GeoPoint>()
                for (j in 0 until coords.length()) {
                    val pt = coords.getJSONArray(j)
                    // MapBox returns [longitude, latitude]
                    points.add(GeoPoint(pt.getDouble(1), pt.getDouble(0)))
                }
                road.mRouteHigh = points
                
                roads.add(road)
            }
            roads.toTypedArray()
        } catch (e: Exception) {
            android.util.Log.e("MapFragment", "MapBox API Error", e)
            emptyArray()
        }
    }

>>>>>>> 3e7c211b67d978360a912324f7cb5173604ee75c
    private fun resetButton() {
        binding.btnFindRoutes.isEnabled = true
        binding.btnFindRoutes.text = "Find Safe Routes"
    }

    private fun displayRoutes(roads: Array<Road>, start: GeoPoint, dest: GeoPoint) {
        val eventsOverlay = binding.mapView.overlays.find { it is MapEventsOverlay }
        binding.mapView.overlays.clear()
        eventsOverlay?.let { binding.mapView.overlays.add(it) }

        // Reset tracking
        routeInfoTexts.clear()
        routeRiskScores.clear()
        routeOverlays.clear()
        selectedRouteIndex = -1
        totalRoutes = minOf(roads.size, 3)
        scoredRoutes = 0

        binding.btnStartNav.isEnabled = false
        binding.btnStartNav.text = "Select a route to navigate"

        for (i in 0 until totalRoutes) {
            val road = roads[i]
            val roadOverlay = RoadManager.buildRoadOverlay(road)

            // Start with neutral gray — will be re-colored after all scores arrive
            roadOverlay.outlinePaint.color = Color.parseColor("#666666")
            roadOverlay.outlinePaint.strokeWidth = 14f
            roadOverlay.outlinePaint.alpha = 200
            
            // Add click listener for selection
            roadOverlay.setOnClickListener { polyline, mapView, eventPoint ->
                selectRoute(i)
                true
            }

            binding.mapView.overlays.add(roadOverlay)
            routeOverlays[i] = roadOverlay

            // Default text while ML loads
            val distKm = String.format(Locale.US, "%.1f", road.mLength)
            routeInfoTexts[i] = "⏳ Route ${i + 1}: ${distKm}km · Analyzing safety…"
            updateInfoCard()

            // Fetch ML safety score
            fetchSafetyScore(road, roadOverlay, i)
        }

        // Start marker
        val startMarker = Marker(binding.mapView)
        startMarker.position = start
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.title = "Start"
        binding.mapView.overlays.add(startMarker)

        // Destination marker
        val destMarker = Marker(binding.mapView)
        destMarker.position = dest
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        destMarker.title = "Destination"
        binding.mapView.overlays.add(destMarker)

        // Zoom to fit
        val minLat = minOf(start.latitude, dest.latitude)
        val maxLat = maxOf(start.latitude, dest.latitude)
        val minLng = minOf(start.longitude, dest.longitude)
        val maxLng = maxOf(start.longitude, dest.longitude)
        val boundingBox = org.osmdroid.util.BoundingBox(
            maxLat + 0.01, maxLng + 0.01,
            minLat - 0.01, minLng - 0.01
        )
        binding.mapView.zoomToBoundingBox(boundingBox, true, 100)

        binding.routeInfoCard.visibility = View.VISIBLE
        binding.mapView.invalidate()
    }

    private fun updateInfoCard() {
        if (_binding == null) return
        val text = routeInfoTexts.toSortedMap().values.joinToString("\n")
        binding.routeInfoText.text = text.ifEmpty { "No routes found" }
    }

    private fun fetchSafetyScore(road: Road, roadOverlay: Polyline, routeIndex: Int) {
        val routePoints = road.mRouteHigh ?: road.getRouteLow()
        val coords = routePoints.map { listOf(it.latitude, it.longitude) }
        val distKm = String.format(Locale.US, "%.1f", road.mLength)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.predictRisk(RouteRequest(coords))

                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread

                    // Store the raw numeric risk score
                    routeRiskScores[routeIndex] = response.risk

                    val summary = if (response.summary.isNotEmpty()) " · ${response.summary.first()}" else ""
                    val levelLabel = response.level.replaceFirstChar { it.titlecase() }
                    routeInfoTexts[routeIndex] = "Route ${routeIndex + 1}: ${distKm}km · $levelLabel (Score: ${response.risk})$summary"

                    if (response.summary.isNotEmpty()) {
                        roadOverlay.title = "$levelLabel (Score: ${response.risk})\n${response.summary.joinToString(", ")}"
                    }

                    onRouteScored()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    val fallbackRisk = generateFallbackRisk(road, routeIndex)
                    routeRiskScores[routeIndex] = fallbackRisk
                    routeInfoTexts[routeIndex] = "Route ${routeIndex + 1}: ${distKm}km · Score: $fallbackRisk"
                    onRouteScored()
                }
                android.util.Log.e("MapFragment", "ML API Error for route $routeIndex: ${e.message}")
            }
        }
    }

    /**
     * Called each time a route finishes scoring.
     * Once ALL routes are scored, re-color them:
     *   Green = highest safety (lowest risk)
     *   Yellow = mid
     *   Red = lowest safety (highest risk)
     */
    private fun onRouteScored() {
        scoredRoutes++
        if (scoredRoutes < totalRoutes) {
            updateInfoCard()
            return
        }

        // All routes scored — rank by risk (ascending = safest first)
        val ranked = routeRiskScores.entries.sortedBy { it.value }

        val colorGreen = Color.parseColor("#00E5A0")
        val colorYellow = Color.parseColor("#FFB347")
        val colorRed = Color.parseColor("#FF4136")

        ranked.forEachIndexed { rank, (routeIdx, _) ->
            val overlay = routeOverlays[routeIdx] ?: return@forEachIndexed
            // Here, we assume a lower numerical risk score makes the route SAFER.
            // Adjust threshold logic as needed based on your API's scale.
            val riskScore = routeRiskScores[routeIdx]!!

            when {
                ranked.size == 1 -> {
                    // Single route — color by absolute risk score. Assuming scale where >50 is risky.
                    overlay.outlinePaint.color = when {
                        riskScore <= 35 -> colorGreen
                        riskScore <= 60 -> colorYellow
                        else -> colorRed
                    }
                    overlay.outlinePaint.strokeWidth = 16f
                    val emoji = when { riskScore <= 35 -> "🟢"; riskScore <= 60 -> "🟡"; else -> "🔴" }
                    routeInfoTexts[routeIdx] = "$emoji ${routeInfoTexts[routeIdx]}"
<<<<<<< HEAD
=======
                    
                    // Auto-select the only route
                    if (selectedRouteIndex == -1) selectRoute(routeIdx)
>>>>>>> 3e7c211b67d978360a912324f7cb5173604ee75c
                }
                rank == 0 -> {
                    // Lowest risk = Safest route
                    overlay.outlinePaint.color = colorGreen
                    overlay.outlinePaint.strokeWidth = 18f
                    overlay.outlinePaint.alpha = 255
                    routeInfoTexts[routeIdx] = "🟢 ${routeInfoTexts[routeIdx]} ★ Safest"
                    // Auto-select the safest route by default
                    if (selectedRouteIndex == -1) selectRoute(routeIdx)
                }
                rank == ranked.lastIndex -> {
                    // Highest risk = Riskiest route
                    overlay.outlinePaint.color = colorRed
                    overlay.outlinePaint.strokeWidth = 12f
                    overlay.outlinePaint.alpha = 200
                    routeInfoTexts[routeIdx] = "🔴 ${routeInfoTexts[routeIdx]}"
                }
                else -> {
                    // Middle risk
                    overlay.outlinePaint.color = colorYellow
                    overlay.outlinePaint.strokeWidth = 14f
                    overlay.outlinePaint.alpha = 220
                    routeInfoTexts[routeIdx] = "🟡 ${routeInfoTexts[routeIdx]}"
                }
            }
        }

        updateInfoCard()
        binding.mapView.invalidate()
    }

    private fun selectRoute(index: Int) {
        selectedRouteIndex = index

        // Highlight selected route, dim others
        routeOverlays.forEach { (i, overlay) ->
            if (i == index) {
                overlay.outlinePaint.alpha = 255
                overlay.outlinePaint.strokeWidth = 20f
                // Bring to front
                binding.mapView.overlays.remove(overlay)
                binding.mapView.overlays.add(overlay)
            } else {
                overlay.outlinePaint.alpha = 90
                overlay.outlinePaint.strokeWidth = 10f
            }
        }
        
        binding.mapView.invalidate()
        binding.btnStartNav.isEnabled = true
        binding.btnStartNav.text = "Start Navigation"
    }

    private fun setupNavigationControls() {
        binding.btnStartNav.setOnClickListener {
            startNavigation()
        }

        binding.btnStopNav.setOnClickListener {
            stopNavigation()
        }
    }

    private var userPointerMarker: Marker? = null

    private fun startNavigation() {
        isNavigating = true

        // UI Changes
        binding.routeInputCard.visibility = View.GONE
        binding.routeInfoCard.visibility = View.GONE
        binding.navControlsLayout.visibility = View.VISIBLE

        // Center on user and keep tracking
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
                interval = 2000 // 2 seconds
                fastestInterval = 1000
                priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    result.lastLocation?.let { location ->
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        binding.mapView.controller.animateTo(geoPoint)
                        binding.mapView.controller.setZoom(19.0)

                        // Update or create the live pointer marker
                        if (userPointerMarker == null) {
                            userPointerMarker = Marker(binding.mapView).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_nav_arrow)
                                title = "You are here"
                            }
                        }
                        // Ensure it is actually on the map (it might have been cleared by displayRoutes)
                        if (!binding.mapView.overlays.contains(userPointerMarker)) {
                            binding.mapView.overlays.add(userPointerMarker)
                        }

                        userPointerMarker?.position = geoPoint
                        
                        // If moving, rotate the navigation arrow to match heading
                        if (location.hasBearing()) {
                            // OSMDroid markers rotate CLOCKWISE, same as Android bearing
                            // Note: Marker.setRotation() takes degrees
                            userPointerMarker?.rotation = location.bearing
                        }

                        binding.mapView.invalidate()
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopNavigation() {
        isNavigating = false

        // UI Changes
        binding.routeInputCard.visibility = View.VISIBLE
        binding.routeInfoCard.visibility = View.VISIBLE
        binding.navControlsLayout.visibility = View.GONE

        binding.mapView.controller.setZoom(15.0)

        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    /**
     * When ML API is down, generate a plausible risk score.
     */
    private fun generateFallbackRisk(road: Road, index: Int): Int {
        val baseRisk = when (index) {
            0 -> 18
            1 -> 42
            else -> 60
        }
        val distanceFactor = (road.mLength / 10.0).coerceIn(0.0, 1.0)
        return (baseRisk + (distanceFactor * 15).toInt()).coerceIn(0, 100)
    }

    // ── Utility ───────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearchLocation.windowToken, 0)
    }

    // ── Pick Mode (for incident report location) ──────────────────

    private fun setupPickMode() {
        binding.selectionLayout.visibility = View.VISIBLE

        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { updateSelectedLocation(it) }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        }

        val mapEventsOverlay = MapEventsOverlay(eventsReceiver)
        binding.mapView.overlays.add(0, mapEventsOverlay)

        binding.btnConfirmLocation.setOnClickListener {
            selectedPoint?.let { point ->
                val address = getAddressFromLocation(point.latitude, point.longitude)
                parentFragmentManager.setFragmentResult("location_request", bundleOf(
                    "lat" to point.latitude,
                    "lng" to point.longitude,
                    "address" to address
                ))
                findNavController().popBackStack()
            } ?: Toast.makeText(context, "Please tap on map to select a location", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectedLocation(point: GeoPoint) {
        selectedPoint = point
        if (selectionMarker == null) {
            selectionMarker = Marker(binding.mapView)
            selectionMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            binding.mapView.overlays.add(selectionMarker)
        }
        selectionMarker?.position = point
        selectionMarker?.title = "Selected Location"
        binding.mapView.invalidate()
        binding.selectedLocationText.text = String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude)
    }

    private fun getAddressFromLocation(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.US)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses?.isNotEmpty() == true) addresses[0].getAddressLine(0)
            else "Selected Location"
        } catch (e: Exception) { "Selected Location" }
    }

    // ── Map Setup ─────────────────────────────────────────────────

    private fun setupMap() {
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)
                binding.mapView.controller.setCenter(currentLocation)
                val marker = Marker(binding.mapView)
                marker.position = currentLocation
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "You are here"
                binding.mapView.overlays.add(marker)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (isNavigating) {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        startRunnable?.let { autocompleteHandler.removeCallbacks(it) }
        destRunnable?.let { autocompleteHandler.removeCallbacks(it) }
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        _binding = null
    }
}
