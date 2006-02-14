package org.openmrs.api.db.hibernate;

import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PatientSetService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.PatientSetDAO;
import org.openmrs.reporting.PatientSet;
import org.openmrs.util.OpenmrsConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class HibernatePatientSetDAO implements PatientSetDAO {

	protected final Log log = LogFactory.getLog(getClass());
	
	private Context context;
	
	public HibernatePatientSetDAO() { }
	
	public HibernatePatientSetDAO(Context c) {
		this.context = c;
	}
	
	public String exportXml(PatientSet ps) throws DAOException {
		// TODO: This is inefficient for large patient sets.
		StringBuffer ret = new StringBuffer("<patientset>");
		for (Integer patientId : ps.getPatientIds()) {
			ret.append(exportXml(patientId));
		}
		ret.append("</patientset>");
		return ret.toString();
	}

	private String formatUserName(User u) {
		StringBuilder sb = new StringBuilder();
		boolean any = false;
		if (u.getFirstName() != null) {
			if (any) {
				sb.append(" ");
			} else {
				any = true;
			}
			sb.append(u.getFirstName());
		}
		if (u.getMiddleName() != null) {
			if (any) {
				sb.append(" ");
			} else {
				any = true;
			}
			sb.append(u.getMiddleName());
		}
		if (u.getLastName() != null) {
			if (any) {
				sb.append(" ");
			} else {
				any = true;
			}
			sb.append(u.getLastName());
		}
		return sb.toString();
	}
	
	private String formatUser(User u) {
		StringBuilder ret = new StringBuilder();
		ret.append(u.getUserId() + "^" + formatUserName(u));
		return ret.toString();
	}

	DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private Element obsElementHelper(Document doc, Locale locale, Obs obs) {
		Element obsNode = doc.createElement("obs");
		Concept c = obs.getConcept();

		obsNode.setAttribute("obs_id", obs.getObsId().toString());
		obsNode.setAttribute("concept_id", c.getConceptId().toString());
		obsNode.setAttribute("concept_name", c.getName(locale).getName());
		
		if (obs.getObsDatetime() != null) {
			obsNode.setAttribute("datetime", df.format(obs.getObsDatetime()));
		}
		if (obs.getAccessionNumber() != null) {
			obsNode.setAttribute("accession_number", obs.getAccessionNumber());
		}
		if (obs.getComment() != null) {
			obsNode.setAttribute("comment", obs.getComment());
		}
		if (obs.getDateStarted() != null) {
			obsNode.setAttribute("date_started", df.format(obs.getDateStarted()));
		}
		if (obs.getDateStopped() != null) {
			obsNode.setAttribute("date_stopped", df.format(obs.getDateStopped()));
		}
		if (obs.getObsGroupId() != null) {
			obsNode.setAttribute("obs_group_id", obs.getObsGroupId().toString());
		}
		if (obs.getValueGroupId() != null) {
			obsNode.setAttribute("value_group_id", obs.getValueGroupId().toString());
		}

		String value = null;
		String dataType = null;
		
		if (obs.getValueCoded() != null) {
			Concept valueConcept = obs.getValueCoded();
			obsNode.setAttribute("value_coded_id", valueConcept.getConceptId().toString());
			obsNode.setAttribute("value_coded", valueConcept.getName(locale).getName());
			dataType = "coded";
			value = valueConcept.getName(locale).getName();
		}
		if (obs.getValueBoolean() != null) {
			obsNode.setAttribute("value_boolean", obs.getValueBoolean().toString());
			dataType = "boolean";
			value = obs.getValueBoolean().toString();
		}
		if (obs.getValueDatetime() != null) {
			obsNode.setAttribute("value_datetime", df.format(obs.getValueDatetime()));
			dataType = "datetime";
			value = obs.getValueDatetime().toString();
		}
		if (obs.getValueNumeric() != null) {
			obsNode.setAttribute("value_numeric", obs.getValueNumeric().toString());
			dataType = "numeric";
			value = obs.getValueNumeric().toString();
		}
		if (obs.getValueText() != null) {
			obsNode.setAttribute("value_text", obs.getValueText());
			dataType = "text";
			value = obs.getValueText();
		}
		if (obs.getValueModifier() != null) {
			obsNode.setAttribute("value_modifier", obs.getValueModifier());
			if (value != null) {
				value = obs.getValueModifier() + " " + value;
			}
		}
		obsNode.setAttribute("data_type", dataType);
		obsNode.appendChild(doc.createTextNode(value));
		
		return obsNode;
	}
	
	/**
	 * Note that the formatting may depend on locale
	 */
	public String exportXml(Integer patientId) throws DAOException {
		Locale locale = context.getLocale();
		
	    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    Document doc = null;
	    
		PatientService patientService = context.getPatientService();
		EncounterService encounterService = context.getEncounterService();

		Patient p = patientService.getPatient(patientId);
		List<Encounter> encounters = encounterService.getEncountersByPatientId(patientId, false);
	    
	    try {
	    	DocumentBuilder builder = factory.newDocumentBuilder();
	        doc = builder.newDocument();
	        
			Element root = (Element) doc.createElement("patient_data");
			doc.appendChild(root);
			
			Element patientNode = doc.createElement("patient");
			patientNode.setAttribute("patient_id", p.getPatientId().toString());
			patientNode.setAttribute("gender", p.getGender());
			if (p.getRace() != null) {
				patientNode.setAttribute("race", p.getRace());
			}
			if (p.getBirthdate() != null) {
				patientNode.setAttribute("birthdate", df.format(p.getBirthdate()));
			}
			if (p.getBirthdateEstimated() != null) {
				patientNode.setAttribute("birthdate_estimated", p.getBirthdateEstimated().toString());
			}
			if (p.getBirthplace() != null) {
				patientNode.setAttribute("birthplace", p.getBirthplace());
			}
			if (p.getCitizenship() != null) {
				patientNode.setAttribute("citizenship", p.getCitizenship());
			}
			if (p.getTribe() != null) {
				patientNode.setAttribute("tribe", p.getTribe().getName());
			}
			if (p.getMothersName() != null) {
				patientNode.setAttribute("mothers_name", p.getMothersName());
			}
			if (p.getCivilStatus() != null) {
				patientNode.setAttribute("civil_status", OpenmrsConstants.CIVIL_STATUS().get(p.getCivilStatus()));
			}
			if (p.getDeathDate() != null) {
				patientNode.setAttribute("death_date", df.format(p.getDeathDate()));
			}
			if (p.getCauseOfDeath() != null) {
				patientNode.setAttribute("cause_of_death", p.getCauseOfDeath());
			}
			if (p.getHealthDistrict() != null) {
				patientNode.setAttribute("health_district", p.getHealthDistrict());
			}
			if (p.getHealthCenter() != null) {
				patientNode.setAttribute("health_center", encounterService.getLocation(p.getHealthCenter()).getName());
				patientNode.setAttribute("health_center_id", p.getHealthCenter().toString());
			}
			
			for (Encounter e : encounters) {
				Element encounterNode = doc.createElement("encounter");
				if (e.getEncounterDatetime() != null) {
					encounterNode.setAttribute("datetime", df.format(e.getEncounterDatetime()));
				}
				
				Element metadataNode = doc.createElement("metadata");
				{
					Location l = e.getLocation();
					if (l != null) {
						Element temp = doc.createElement("location");
						temp.setAttribute("location_id", l.getLocationId().toString());
						temp.appendChild(doc.createTextNode(l.getName()));
						metadataNode.appendChild(temp);
					}
					EncounterType t = e.getEncounterType();
					if (t != null) {
						Element temp = doc.createElement("encounter_type");
						temp.setAttribute("encounter_type_id", t.getEncounterTypeId().toString());
						temp.appendChild(doc.createTextNode(t.getName()));
						metadataNode.appendChild(temp);
					}
					Form f = e.getForm();
					if (f != null) {
						Element temp = doc.createElement("form_id");
						temp.setAttribute("form_id", f.getFormId().toString());
						temp.appendChild(doc.createTextNode(f.getName()));
						metadataNode.appendChild(temp);
					}
					User u = e.getProvider();
					if (u != null) {
						Element temp = doc.createElement("provider");
						temp.setAttribute("provider_id", u.getUserId().toString());
						temp.appendChild(doc.createTextNode(formatUserName(u)));
						metadataNode.appendChild(temp);
					}
				}
				encounterNode.appendChild(metadataNode);

				Collection<Obs> observations = e.getObs();
				if (observations != null && observations.size() > 0) {
					Element observationsNode = doc.createElement("observations");
					for (Obs obs : observations) {
						Element obsNode = obsElementHelper(doc, locale, obs);
						observationsNode.appendChild(obsNode);
					}
					encounterNode.appendChild(observationsNode);
				}
				
				Set<Order> orders = e.getOrders();
				if (orders != null && orders.size() != 0) {
					Element ordersNode = doc.createElement("orders");
					for (Order order : orders) {
						Element orderNode = doc.createElement("order");
						orderNode.setAttribute("order_id", order.getOrderId().toString());
						orderNode.setAttribute("order_type", order.getOrderType().getName());

						Concept concept = order.getConcept();
						orderNode.setAttribute("concept_id", concept.getConceptId().toString());
						orderNode.setAttribute("concept_name", concept.getName(locale).getName());

						if (order.getInstructions() != null) {
							orderNode.setAttribute("instructions", order.getInstructions());
						}
						if (order.getStartDate() != null) {
							orderNode.setAttribute("start_date", order.getStartDate().toString());
						}
						if (order.getAutoExpireDate() != null) {
							orderNode.setAttribute("auto_expire_date", df.format(order.getAutoExpireDate()));
						}
						if (order.getOrderer() != null) {
							orderNode.setAttribute("orderer", formatUser(order.getOrderer()));
						}
						if (order.isDiscontinued() != null) {
							orderNode.setAttribute("discontinued", order.isDiscontinued().toString());
						}
						if (order.getDiscontinuedDate() != null) {
							orderNode.setAttribute("discontinued_date", df.format(order.getDiscontinuedDate()));
						}
						if (order.getDiscontinuedReason() != null) {
							orderNode.setAttribute("discontinued_reason", order.getDiscontinuedReason());
						}

						ordersNode.appendChild(orderNode);
					}
				}
				
				patientNode.appendChild(encounterNode);
			}
			
			ObsService obsService = context.getObsService();
			Set<Obs> allObservations = obsService.getObservations(p);
			if (allObservations != null && allObservations.size() > 0) {
				Set<Obs> undoneObservations = new HashSet<Obs>();
				for (Obs obs : allObservations) {
					if (obs.getEncounter().getEncounterId() != null) {
						undoneObservations.add(obs);
					}
				}

				if (undoneObservations.size() > 0) {
					Element observationsNode = doc.createElement("observations");
					for (Obs obs : undoneObservations) {
						Element obsNode = obsElementHelper(doc, locale, obs);
						observationsNode.appendChild(obsNode);
					}
					patientNode.appendChild(observationsNode);
				}
			}

			// TODO: put in orders that don't belong to any encounter
			
			root.appendChild(patientNode);

	    } catch (Exception ex) {
			throw new DAOException(ex);
		}
				
		String ret = null;

		try {
			Source source = new DOMSource(doc);
			StringWriter sw = new StringWriter();
			Result result = new StreamResult(sw);
			
			Transformer xformer = TransformerFactory.newInstance().newTransformer();
			xformer.transform(source, result);
			ret = sw.toString();
		} catch (Exception ex) {
			throw new DAOException(ex);
		}
		
		return ret;
	}
	
	public PatientSet getPatientsHavingNumericObs(Integer conceptId, PatientSetService.Modifier modifier, Number value) {
		Session session = HibernateUtil.currentSession();
		HibernateUtil.beginTransaction();
		
		Query query;
		StringBuffer sb = new StringBuffer();
		sb.append("select patient_id from obs o " +
				"where concept_id = :concept_id ");
		boolean useVal = false;
		if (value != null && modifier != PatientSetService.Modifier.EXISTS) {
			sb.append("and value_numeric " + modifier.getSqlRepresentation() + " :value ");
			useVal = true;
		} else {
			sb.append("and value_numeric is not null ");
		}
		sb.append("group by patient_id ");
		query = session.createSQLQuery(sb.toString());
		query.setInteger("concept_id", conceptId);
		if (useVal) {
			query.setDouble("value", value.doubleValue());
		}

		PatientSet ret = new PatientSet();
		List patientIds = query.list();
		ret.setPatientIds(new HashSet<Integer>(patientIds));

		HibernateUtil.commitTransaction();
		
		return ret;
	}
	
	public PatientSet getPatientsByCharacteristics(String gender, Date minBirthdate, Date maxBirthdate) throws DAOException {
		Session session = HibernateUtil.currentSession();
		HibernateUtil.beginTransaction();
		
		Query query;
		
		StringBuffer queryString = new StringBuffer("select patientId from Patient patient");
		List<String> clauses = new ArrayList<String>();

		clauses.add("patient.voided = false");
		
		if (gender != null) {
			gender = gender.toUpperCase();
			clauses.add("patient.gender = :gender");
		}
		if (minBirthdate != null) {
			clauses.add("patient.birthdate >= :minBirthdate");
		}
		if (maxBirthdate != null) {
			clauses.add("patient.birthdate <= :maxBirthdate");
		}
		
		boolean first = true;
		for (String clause : clauses) {
			if (first) {
				queryString.append(" where ").append(clause);
				first = false;
			} else {
				queryString.append(" and ").append(clause);
			}
		}
		query = session.createQuery(queryString.toString());
		if (gender != null) {
			query.setString("gender", gender);
		}
		if (minBirthdate != null) {
			query.setDate("minBirthdate", minBirthdate);
		}
		if (maxBirthdate != null) {
			query.setDate("maxBirthdate", maxBirthdate);
		}
		
		List<Integer> patientIds = query.list();
		
		PatientSet ret = new PatientSet();
		ret.setPatientIds(new HashSet<Integer>(patientIds));

		HibernateUtil.commitTransaction();
		
		return ret;
	}

	private static final long MS_PER_YEAR = 365l * 24 * 60 * 60 * 1000l; 
	
	public Map<Integer, String> getShortPatientDescriptions(PatientSet patients) throws DAOException {
		Session session = HibernateUtil.currentSession();
		HibernateUtil.beginTransaction();
		
		Map<Integer, String> ret = new HashMap<Integer, String>();
		
		Set<Integer> ids = patients.getPatientIds();
		Query query = session.createQuery("select patient.patientId, patient.gender, patient.birthdate from Patient patient");
		
		List<Object[]> temp = query.list();
		
		long now = System.currentTimeMillis();
		for (Object[] results : temp) {
			if (!ids.contains(results[0])) { continue; }
			StringBuffer sb = new StringBuffer();
			if ("M".equals(results[1])) {
				sb.append("Male");
			} else {
				sb.append("Female");
			}
			Date bd = (Date) results[2];
			if (bd != null) {
				int age = (int) ((now - bd.getTime()) / MS_PER_YEAR);
				sb.append(", ").append(age).append(" years old");
			}
			ret.put((Integer) results[0], sb.toString()); 
		}
		
		HibernateUtil.commitTransaction();
		
		return ret;
	}
	
	public Map<Integer, List<Obs>> getObservations(PatientSet patients, Concept concept) throws DAOException {
		Session session = HibernateUtil.currentSession();
		HibernateUtil.beginTransaction();
		
		Map<Integer, List<Obs>> ret = new HashMap<Integer, List<Obs>>();
		
		Set<Integer> ids = patients.getPatientIds();
		
		/*
		Query query = session.createQuery("select obs, obs.patientId " +
										  "from Obs obs where obs.conceptId = :conceptId " +
										  " and obs.patientId in :ids " +
										  "order by obs.obsDatetime asc");
		query.setInteger("conceptId", conceptId);
		query.set
	
		List<Object[]> temp = query.list();
		for (Object[] holder : temp) {
			Obs obs = (Obs) holder[0];
			Integer ptId = (Integer) holder[1];
			List<Obs> forPatient = ret.get(ptId);
			if (forPatient == null) {
				forPatient = new ArrayList<Obs>();
				ret.put(ptId, forPatient);
			}
			forPatient.add(obs);
		}
		*/
		Criteria criteria = session.createCriteria(Obs.class);
		criteria.add(Restrictions.eq("concept", concept));
		criteria.add(Restrictions.in("patient.patientId", ids));
		criteria.addOrder(org.hibernate.criterion.Order.asc("obsDatetime"));
		log.debug("criteria: " + criteria);
		List<Obs> temp = criteria.list();
		for (Obs obs : temp) {
			Integer ptId = obs.getPatient().getPatientId();
			List<Obs> forPatient = ret.get(ptId);
			if (forPatient == null) {
				forPatient = new ArrayList<Obs>();
				ret.put(ptId, forPatient);
			}
			forPatient.add(obs);
		}
				
		HibernateUtil.commitTransaction();
		
		return ret;
	}
	
}
