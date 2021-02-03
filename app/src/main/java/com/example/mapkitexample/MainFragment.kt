package com.example.mapkitexample

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.SubpolylineHelper
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.location.LocationStatus
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.*
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.Error
import com.yandex.runtime.ui_view.ViewProvider
import kotlinx.android.synthetic.main.fragment_main.view.*

private const val API_KEY = "Тут должен быть ваш API ключ к mapkit"

class MainFragment : Fragment(), UserLocationObjectListener, LocationListener,
    MapObjectTapListener, Session.RouteListener {

    private lateinit var mapView: MapView
    private lateinit var userLocationLayer: UserLocationLayer
    private lateinit var locationManager: LocationManager
    private lateinit var marksObject: MapObjectCollection
    private lateinit var routesObject: MapObjectCollection
    private lateinit var mtRouter: MasstransitRouter

    private var currentUserPosition: Point? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.setApiKey(API_KEY)
        MapKitFactory.initialize(requireContext())
        TransportFactory.initialize(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?)
    : View? {
        val view = inflater.inflate(R.layout.fragment_main, container, false)

        mapView = view.findViewById(R.id.main_mapview)
        userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.mapWindow)
        locationManager = MapKitFactory.getInstance().createLocationManager()
        marksObject = mapView.map.mapObjects.addCollection()
        routesObject = mapView.map.mapObjects.addCollection()
        mtRouter = TransportFactory.getInstance().createMasstransitRouter()

        locationManager.requestSingleUpdate(this)

        // двигаем камеру поближе к Москве
        mapView.map.move(
            CameraPosition(Point(55.751574, 37.573856), 11f, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0f),
            null
        )

        setUserLocation()

        view.user_location.setOnClickListener {

            val gpsLoactionManager: android.location.LocationManager =
                (requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager)

            val enable = gpsLoactionManager.isProviderEnabled(
                android.location.LocationManager.GPS_PROVIDER)

            // перемещение к местоположению пользователя
            if (enable && currentUserPosition != null) {
                mapView.map.move(
                    CameraPosition(currentUserPosition as Point, 14f, 0f, 0f),
                    Animation(Animation.Type.SMOOTH, 0f), null)
            }

            // построение и отображение маршрута
            if (currentUserPosition != null) {
                buildRoute(currentUserPosition as Point,
                        Point(56.8301, 60.6195),
                        Point(56.8315, 60.6299),
                        this)
            }
        }

        // добавление маркеров
        addMark(Point(56.8301, 60.6195))
        addMark(Point(56.8315, 60.6299))

        return view
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        userLocationLayer.setAnchor(
            PointF((mapView.width * 0.5).toFloat(),
                (mapView.height * 0.5).toFloat()),

            PointF((mapView.width * 0.5).toFloat(),
                (mapView.height * 0.5).toFloat())
        )

        userLocationView.accuracyCircle.strokeColor = Color.YELLOW
        userLocationView.accuracyCircle.strokeWidth = 0.5f
    }

    override fun onObjectRemoved(p0: UserLocationView) {

    }

    override fun onObjectUpdated(p0: UserLocationView, p1: ObjectEvent) {

    }

    // вызывается при изменении местоположения
    override fun onLocationUpdated(p0: Location) {
        currentUserPosition = p0.position
    }

    // показывает статус местоположения (доступно или нет)
    override fun onLocationStatusUpdated(p0: LocationStatus) {

    }

    // вызывается при клике на маркер
    override fun onMapObjectTap(p0: MapObject, p1: Point): Boolean {
        Log.d("MAINFRAGMENT", "TAPMARK")
        return true
    }

    // отображает маршрут (вызывается из buildRoute)
    override fun onMasstransitRoutes(p0: MutableList<Route>) {
        masstransitRoutes(p0)
    }

    // отображает ошибку
    override fun onMasstransitRoutesError(p0: Error) {

    }

    private fun setUserLocation() {
        userLocationLayer.isVisible = true
        userLocationLayer.isHeadingEnabled = true
        // устанавливаем слушателя
        userLocationLayer.setObjectListener(this)
    }

    // добавляет маркер на карту и устанавливает слушателя
    private fun addMark(point: Point)
    {
        // создаем view маркера и устанавилваем ему картинку
        val createMark = getMark(R.drawable.ic_start_mark)

        // создание маркера
        val mark = marksObject.addPlacemark(point, ViewProvider(createMark))

        mark.addTapListener(this)
    }

    // создает маркер
    private fun getMark(imageId: Int): View {
        val mark = View(context).apply {
            background = context.getDrawable(imageId)
        }
        return mark
    }

    // строит маршрут (отображает)
    private fun masstransitRoutes(routes: MutableList<Route>) {
        if (routes.size > 0)
            for (section in routes[0].sections) {
                val routesPolylineMapObject = routesObject.addPolyline(
                        SubpolylineHelper.subpolyline(
                                routes[0].geometry, section.geometry))
                routesPolylineMapObject.strokeColor = 0xFF000000.toInt()
                routesPolylineMapObject.gapLength = 3.0f
                routesPolylineMapObject.dashLength = 15.0f
            }
    }

    // показывает через какие точки строить маршрут и вызывает функциию для построения
    private fun buildRoute(pointStart: Point,
                           pointMedium: Point,
                           pointFinish: Point,
                           listenerFoot: Session.RouteListener,)
    {
        val points = mutableListOf<RequestPoint>()

        //Добавляем позицию начальной точки
        points.add(RequestPoint(pointStart, RequestPointType.WAYPOINT, null))

        //Добавляем позицию промежуточной точки
        points.add(RequestPoint(pointMedium, RequestPointType.VIAPOINT, null))

        // Добавляем позицию конченой точки
        points.add(RequestPoint(pointFinish, RequestPointType.WAYPOINT, null))

        val options = MasstransitOptions(listOf(), listOf(), TimeOptions())
        mtRouter.requestRoutes(points, options, listenerFoot)
    }
}