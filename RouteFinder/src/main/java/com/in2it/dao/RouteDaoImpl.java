package com.in2it.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.in2it.model.Planet;
import com.in2it.model.PlanetGraph;
import com.in2it.model.RouteEdge;

@Repository
public class RouteDaoImpl implements RouteDao {
	private Logger logger = LoggerFactory.getLogger(RouteDaoImpl.class);
	@Autowired
	private EntityManagerFactory entityManagerFactory;
	private SessionFactory sessionFactory;

	@Override
	public void saveGraph(PlanetGraph graph) {
		if (graph == null) {
			logger.info("Graph is null cant save");
			return;
		}
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		session.save(graph);
		for (Planet planet : graph.getPlannets()) {
			session.save(planet);
			for (RouteEdge edge : planet.getRouteEdges()) {
				session.save(edge);
			}
		}
		session.getTransaction().commit();
		session.close();
	}

	@SuppressWarnings("unchecked")
	@Override
	/**
	 * This method will return graph At this time only one graph will be in the
	 * memory so we are not considering graphId while retrieving
	 * 
	 * @param edge
	 * @param graphId
	 */
	// FIxMe: add criteria for using graphId
	public PlanetGraph getGraph() {
		Session session = sessionFactory.openSession();
		List<PlanetGraph> graphs = session.createCriteria(PlanetGraph.class).list();
		session.close();
		if (graphs.isEmpty()) {
			logger.info("No graph found into db");
			return null;
		}
		return graphs.get(0);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void addRoute(RouteEdge edge) {
		if (edge == null) {
			return;
		}
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		List<PlanetGraph> graphs = session.createCriteria(PlanetGraph.class).list();
		if (graphs.isEmpty()) {
			logger.info("No graph found into db, can't add route");
			return;
		}
		PlanetGraph graph = graphs.get(0);
		Planet sourcePlanet = getPlanet(graph.getPlannets(), edge.getSource());
		Planet destinationPlanet = getPlanet(graph.getPlannets(), edge.getDetination());
		if (sourcePlanet != null && destinationPlanet != null) {
			session.close();
			updateRoute(edge);
			return;
		}
		// TODO: if no source planned exist create one more graph and add this
		// route. not supporting currently
		if (sourcePlanet == null) {
			logger.info("No source Plannet exist can't add this route");
			return;
		} else {
			Planet destPlanet = new Planet(edge.getDetination());
			RouteEdge revrsePathEdge = new RouteEdge(edge.getDetination(), edge.getSource(), edge.getDistance());
			revrsePathEdge.setPlanet(destPlanet);
			List<RouteEdge> destPlanetRoutes = new ArrayList<>();
			destPlanetRoutes.add(revrsePathEdge);
			destPlanet.setPlanetGraph(graph);
			session.save(destPlanet);
			session.save(revrsePathEdge);
			edge.setPlanet(sourcePlanet);
			sourcePlanet.getRouteEdges().add(edge);
		}
		session.save(graph);
		session.getTransaction().commit();
		session.close();
		logger.info("Route added successfully");
	}

	/**
	 * This method update the distance of matched route
	 * 
	 * @param edge
	 * @param graphId
	 */
	@SuppressWarnings("unchecked")
	@Override
	// Fix me: need to fix duplicate insert while updating and also update
	// bidirectional relationship
	public void updateRoute(RouteEdge edge) {
		if (edge == null) {
			return;
		}
		Session session = sessionFactory.openSession();
		session.beginTransaction();
		List<PlanetGraph> graphs = session.createCriteria(PlanetGraph.class).list();
		if (graphs.isEmpty()) {
			logger.info("No graph found into db, can't add route");
			return;
		}
		PlanetGraph graph = graphs.get(0);
		RouteEdge matchedEdge = getMatchedEdge(graph.getPlannets(), edge);
		// If no matching edge found in planet graph then return
		if (matchedEdge == null) {
			logger.info("No route exist can't update the route");
			return;
		}
		// Update distance of matched route
		matchedEdge.setDistance(edge.getDistance());
		session.saveOrUpdate(edge);
		session.getTransaction().commit();
		session.close();
		logger.info("Route updated successfully");
	}

	private Planet getPlanet(List<Planet> planets, String planetToMatch) {
		Planet matchedPlanet = null;
		for (Planet planet : planets) {
			if (planet != null && planet.getName() != null && planet.getName().equalsIgnoreCase(planetToMatch)) {
				matchedPlanet = planet;
				break;
			}
		}
		return matchedPlanet;
	}

	/**
	 * This method will return matched edge of the planets
	 * 
	 * @param planets
	 * @param edge
	 * @return
	 */
	private RouteEdge getMatchedEdge(List<Planet> planets, RouteEdge edge) {
		if (planets == null || planets.isEmpty() || edge == null) {
			return null;
		}
		for (Planet planet : planets) {
			if (planet != null && planet.getName() != null && planet.getName().equalsIgnoreCase(edge.getSource())) {
				if (planet.getRouteEdges() != null && !planet.getRouteEdges().isEmpty()) {
					for (RouteEdge e : planet.getRouteEdges()) {
						if (e.getDetination().equalsIgnoreCase(edge.getDetination())) {
							return e;
						}
					}
				}

			}
		}
		return null;
	}

	@PostConstruct
	private void init() {
		sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Set<String> getTotalPlanet() {
		Set<String> result = new HashSet<>();
		Session session = sessionFactory.openSession();
		List<Planet> planets = session.createCriteria(Planet.class).list();
		session.close();
		if (planets.isEmpty()) {
			logger.info("No Planets into db");
			return result;
		}
		for (Planet planet : planets) {
			result.add(planet.getName());
		}
		return result;
	}
}
