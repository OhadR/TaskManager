package com.ohadr.cbenchmarkr;

import java.util.*;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.google.appengine.api.datastore.*;
import com.ohadr.auth_flows.core.gae.GAEAuthenticationAccountRepositoryImpl;
import com.ohadr.auth_flows.interfaces.AuthenticationAccountRepository;
import com.ohadr.cbenchmarkr.core.BenchmarkrAuthenticationFlowsRepositoryImpl;
import com.ohadr.cbenchmarkr.core.BenchmarkrAuthenticationUserImpl;
import com.ohadr.cbenchmarkr.interfaces.BenchmarkrAuthenticationUser;
import com.ohadr.cbenchmarkr.interfaces.ITrainee;
import com.ohadr.cbenchmarkr.interfaces.IRepository;
import com.ohadr.cbenchmarkr.utils.StatisticsData;
import com.ohadr.cbenchmarkr.utils.TimedResult;


@Component
public class GAERepositoryImpl implements IRepository
{
	private static final String STATS__NUMBER_OF_REGISTERED_RESULTS = "numberOfRegisteredResults";

	private static final String STATS__NUMBER_OF_REGISTERED_USERS = "numberOfRegisteredUsers";

	//delimiter of the pairs <timestamp $ result> :
	private static final String WORKOUT_PAIR_DELIMITER = "~";
	
	//delimiter of the history-table items:
	private static final String HISTORY_WORKOUT_DELIMITER = "-";
	
	private static Logger log = Logger.getLogger(GAERepositoryImpl.class);
	private static final String USERNAME_PROP_NAME = "username";
	private static final String TOTAL_GRADE_PROP_NAME = "total_grade";

	private static final String USER_DB_KIND = "Users";
	private static final String HISTORY_DB_KIND = "History";
	private static final String STATS_DB_KIND = "Statistics";
	private static final String STATS_KEY = "Statistics"; 

	private static final String NEWSLETTERS_DB_KIND = "NewsLetters";
	private static final String NEWSLETTERS_KEY = "NewsLetters"; 


	@Autowired
	private AuthenticationAccountRepository authFlowsRepository;

	private DatastoreService datastore;

	public GAERepositoryImpl()
	{
		datastore = DatastoreServiceFactory.getDatastoreService();
	}

	/**
	 * this method adds the workout both to "user" table, and to the "hostory" table. It also makes sure that @workout does not already exist. 
	 * @throws BenchmarkrRuntimeException - if this workout already exists
	 */
	@Override
	public void addWorkoutForTrainee(String trainee, Workout workout) throws BenchmarkrRuntimeException 
	{
		log.debug("storing workout " + workout + "for user: " + trainee);
		
		Entity dbUser = getUserEntity( trainee );
		
		//create a new one if does not exist:
		//TODO reconsider whether to throw here an exception. user must exist for user. create him an entry upon registration.
		if( dbUser == null )
		{
			log.info("creating a new entity for user " + trainee );
			
			dbUser = new Entity(USER_DB_KIND, trainee );		//the username is the key
		}

		
//		dbUser.setProperty(USERNAME_NAME, trainee );
		dbUser.setProperty(workout.getName(), workout.getResult() );

		Entity historyEntity = addWorkoutForTraineeInHistoryTable( trainee, workout );
		
		datastore.put( dbUser );
		datastore.put( historyEntity );
	}
	
	/**
	 * add workout to the history table of the trainee. if this workout already exists, it throws an exception
	 * @param trainee
	 * @param workout
	 * @return
	 * @throws BenchmarkrRuntimeException - if this workout already exists
	 */
	private Entity addWorkoutForTraineeInHistoryTable(String trainee, Workout workout) throws BenchmarkrRuntimeException
	{
		Entity historyEntity = getHistoryEntity( trainee );
		//create a new one if does not exist:
		//TODO reconsider whether to throw here an exception. user must exist for user.
		if( historyEntity == null )
		{
			log.info("creating a new entity for user " + trainee );
			
			historyEntity = new Entity(HISTORY_DB_KIND, trainee );		//the username is the key
		}
		
		String property;
		if( historyEntity.hasProperty( workout.getName() ))
		{
			//validate the workout does not already exist:
			validateNonExistence( historyEntity, workout );
			
			property = (String) historyEntity.getProperty(workout.getName() );
			property += HISTORY_WORKOUT_DELIMITER + workout.getDate().getTime() + WORKOUT_PAIR_DELIMITER + workout.getResult();
		}
		else
		{
			property = workout.getDate().getTime() + WORKOUT_PAIR_DELIMITER + workout.getResult();
		}
		historyEntity.setProperty( workout.getName(), property );
		
		return historyEntity;
	}

	
	/**
	 * method makes sure that @workout does not already registered for this user (entity). 
	 * @param historyEntity
	 * @param workout
	 * @throws BenchmarkrRuntimeException 
	 */
	private void validateNonExistence(Entity historyEntity, Workout workout) throws BenchmarkrRuntimeException
	{
		List<TimedResult> registeredWorkouts = getWorkoutHistoryForTraineeEntity( historyEntity, workout.getName() );
		for( TimedResult registeredWorkout : registeredWorkouts )
		{
			if( registeredWorkout.timestamp == workout.getDate().getTime() )
			{
				throw new BenchmarkrRuntimeException( "Workout " + workout.getName() + " already registered for date " + workout.getDate() );
			}
		}		
	}

	public void removeUsersOrders(List<String> usersToRemove) 
	{
		for(String user : usersToRemove)
		{
			Key userKey = KeyFactory.createKey(USER_DB_KIND, user);
			log.info( "deleting user " + user);
			datastore.delete(userKey);
		}
		
	}
	
	private Entity getUserEntity(String username)
	{
		return getEntityFromDB( username, USER_DB_KIND );
	}

	private Entity getHistoryEntity(String username)
	{
		return getEntityFromDB( username, HISTORY_DB_KIND );
	}

	/**
	 * 
	 * @param key
	 * @return the required entity. If does not exist - create one and return it.
	 */
	private Entity getStatisticsEntity(String key)
	{
		Entity dbStats = getEntityFromDB( key, STATS_DB_KIND );
		
		//create a new one if does not exist:
		if( dbStats == null )
		{
			log.info("creating a new entity for key " + key );
			dbStats = new Entity(STATS_DB_KIND, key );
		}
		
		return dbStats;
	}

	private Entity getNewslettersEntity()
	{
		Entity entity = getEntityFromDB( NEWSLETTERS_KEY, NEWSLETTERS_DB_KIND );
		//create a new one if does not exist:
		if( entity == null )
		{
			log.info("creating a new entity for key " + NEWSLETTERS_KEY );
			entity = new Entity(NEWSLETTERS_DB_KIND, NEWSLETTERS_KEY );
		}
		return entity;
	}

	/**
	 * gets an entity from the DB
	 * @param trainee - the key of the entity 
	 * @param kind - the "kind" of DB (table name)
	 * @return
	 */
	private Entity getEntityFromDB(String entityName, String kind)
	{
		Key userKey = KeyFactory.createKey(kind, entityName);
		Entity entity;
		try 
		{
			entity = datastore.get(userKey);
			log.debug("got entity of " + entityName + ": " + entity);
		} 
		catch (EntityNotFoundException e) 
		{
			//this is not an error - if user doesn't exist, we will create an entry for him:
			log.debug("entity of " + entityName + " not found");
			entity = null;
		}

		return entity;
	}

	/**
	 * returns all the trainees that have registered a WOD-result.
	 */
	@Override
	public List<ITrainee> getTrainees()
	{
		log.debug("getAllTrainees()");
		Query q = new Query(USER_DB_KIND);
		PreparedQuery pq = datastore.prepare(q);  

		List<ITrainee> retVal = new ArrayList<ITrainee>();
		
		for (Entity result : pq.asIterable()) 
		{
			ITrainee trainee = convertEntityToTrainee(result);
			
			retVal.add( trainee );
		}		

		return retVal;
	}

	/**
	 * we Cache-Repo calls this method if a Trainee is needed but is not in the cache. 
	 * return a Trainee object, even if he hasn't enter any WOD-result.
	 */
	@Override
	public ITrainee getTrainee(String traineeId)
	{
		Entity dbUser = getUserEntity( traineeId );
		if( dbUser == null )		//this is the case if user hasn't enter any result, so we don't have an entry in "Users" table
		{
			return null;
		}
		
		ITrainee trainee = convertEntityToTrainee( dbUser );
		return trainee;
	}

	private final ITrainee convertEntityToTrainee(Entity entity)
	{
		StringBuffer sb = new StringBuffer();
		String username = (String) entity.getKey().getName();		//the username is the key...
		sb.append(username).append("\n");
		
		//if this is a new trainee, and we did not run "calc grades" yet:
		double totaGrade = 0;
		if( entity.hasProperty( TOTAL_GRADE_PROP_NAME ) )
		{
			totaGrade = (double) entity.getProperty( TOTAL_GRADE_PROP_NAME );
		}


		//get user's first and last name from repo of user-auth:
		String firstName = null;
		String lastName = null;
		boolean isMale = true;
		Date dateOfBirth = null;
		try
		{
			UserDetails authFlowsUser = authFlowsRepository.loadUserByUsername( username );
			BenchmarkrAuthenticationUser inMemoryAuthenticationUser = (BenchmarkrAuthenticationUser)authFlowsUser;
			firstName = inMemoryAuthenticationUser.getFirstName();
			lastName = inMemoryAuthenticationUser.getLastName();
			isMale = inMemoryAuthenticationUser.isMale();
			dateOfBirth = inMemoryAuthenticationUser.getDateOfBirth();
		} 
		catch (UsernameNotFoundException e)
		{
			log.error("[CANNOT HAPPEN]: user " + username + " was not found in the auth-flows DB; cannot retrieve his first+last name", e);
		}
		
		Map<String, Object> properties = entity.getProperties();
		
		Map<String, Integer> traineeResults = new HashMap<String, Integer>();
		for(Map.Entry<String, Object> entry : properties.entrySet())
		{
			//skip name, DOB, weight, grade...
			if( entry.getKey().equals(USERNAME_PROP_NAME) ||
					entry.getKey().equals( TOTAL_GRADE_PROP_NAME ) )
			{
				//skip
				continue;
			}
			Object valObj = entry.getValue();
			Integer val = new Integer(valObj.toString());

			sb.append("putting " + entry.getKey() + " : " + val).append("\n");
			traineeResults.put(entry.getKey(), val);
		}
		log.debug("trainee was read from DB: " + sb.toString());
		final ITrainee trainee = new Trainee( username, 
				firstName,
				lastName,
				traineeResults,
				totaGrade, 
				isMale, 
				dateOfBirth);

		return trainee;
	}

	@Override
	public void updateGradesForTrainees(Map<String, Double> gradesPerTrainee) throws BenchmarkrRuntimeException
	{
		log.debug("updating GradesForTrainees");
		
		for( Map.Entry<String, Double> entry : gradesPerTrainee.entrySet() )
		{
			Entity dbUser = getUserEntity( entry.getKey() );
			
			if( dbUser == null )
			{
				throw new BenchmarkrRuntimeException( "could not update grade for user " + entry.getKey() + " - was not found" );
			}
			
			dbUser.setProperty( TOTAL_GRADE_PROP_NAME, entry.getValue() );
			
			datastore.put( dbUser );
		}
	}

	@Override
	public List<TimedResult> getWorkoutHistoryForTrainee(String trainee,
			String workoutName)
	{
		log.info("get Workout " + workoutName + " history For Trainee " + trainee);

		Entity historyEntity = getHistoryEntity( trainee );

		return getWorkoutHistoryForTraineeEntity( historyEntity, workoutName );
	}
	
	@Deprecated
	private List<TimedResult> getWorkoutHistoryForTraineeEntity(Entity historyEntity,
			String workoutName)
	{
		List<TimedResult> retVal = null;
		
		if( historyEntity == null )
		{
			return null;
		}
		
		String property;
		if( ! historyEntity.hasProperty( workoutName ) )
		{
			return null;
		}
		else
		{
			property = (String) historyEntity.getProperty( workoutName );
			retVal = parseHistoryItem( property );
		}
		
		return retVal;
	}	

	@Override
	public Map<String, List<TimedResult>> getHistoryForTrainee(String trainee) 
	{
		log.info("get history For Trainee " + trainee);

		Entity historyEntity = getHistoryEntity( trainee );

		return getHistoryForTraineeEntity( historyEntity );
	}

	private Map<String, List<TimedResult> > getHistoryForTraineeEntity( Entity historyEntity )
	{
		if( historyEntity == null )
		{
			return null;
		}
		
		Map<String, Object> properties = historyEntity.getProperties();

		Map<String, List<TimedResult>> retVal = new HashMap<String, List<TimedResult>>();
		for( Map.Entry<String, Object> entry : properties.entrySet() )
		{
			Object valObj = entry.getValue();
			String val = new String(valObj.toString());

//			sb.append("putting " + entry.getKey() + " : " + val).append("\n");

			List<TimedResult> timedResults = parseHistoryItem( val );
			retVal.put( entry.getKey(), timedResults );
		}


		
		return retVal;
	}	
	
	
	/**
	 * gets a line (from the history-DB) and parses it into list of TimedResult's
	 * @param line
	 * @return
	 */
	private static List<TimedResult> parseHistoryItem( String line )
	{
		List<TimedResult> retVal = new ArrayList<TimedResult>();
		
		//parse the string:
		String[] workouts = line.split( HISTORY_WORKOUT_DELIMITER );
		for(int i =0; i < workouts.length ; i++)
		//for(String workout : workouts)
		{
			String[] pair = workouts[i].split( WORKOUT_PAIR_DELIMITER );
			TimedResult tr = new TimedResult( Integer.valueOf(pair[1]), Long.valueOf(pair[0]) );
			retVal.add(  tr );
		}	
		return retVal;		
	}


	
	
	@Override
	public void setAdmin(String authenticatedUsername) 
	{
		authFlowsRepository.setAuthority( authenticatedUsername, "ROLE_ADMIN" );
	}

	@Override
	public int getNumberOfRegisteredUsers()
	{
		int retVal = 0;

		//will not work on devserver, but OK on production:
		Query q = new Query("__Stat_Kind__");
		PreparedQuery global = datastore.prepare( q );

		log.debug( global );
		for( Entity globalStat : global.asIterable() )
		{
		    String kindName = (String) globalStat.getProperty("kind_name");
		    if( kindName.equals( GAEAuthenticationAccountRepositoryImpl.AUTH_FLOWS_USER_DB_KIND ) )
		    {
			    Long totalEntities = (Long) globalStat.getProperty("count");
				log.debug("found table named " + kindName + ", num entities= " + totalEntities );
				retVal = totalEntities.intValue();
				break;
		    }
		}
		return retVal;
	}


	@Override
	public void resetRepository()
	{
		log.info( "reset Users Repository..." );
		Query q = new Query( USER_DB_KIND );
		PreparedQuery pq = datastore.prepare( q );  

		for (Entity result : pq.asIterable()) 
		{
			datastore.delete( result.getKey() );
			//disable this user in auth-flows (rather than delete it):
			authFlowsRepository.setDisabled( result.getKey().getName() );
		}		

		log.info( "reset History Repository..." );
		q = new Query( HISTORY_DB_KIND );
		pq = datastore.prepare( q );  

		for (Entity result : pq.asIterable()) 
		{
			datastore.delete( result.getKey() );
		}
		
		log.info( "disabling auth-flows's Users..." );
		q = new Query( GAEAuthenticationAccountRepositoryImpl.AUTH_FLOWS_USER_DB_KIND );
		pq = datastore.prepare( q );  

		for (Entity result : pq.asIterable()) 
		{
//			datastore.delete( result.getKey() );
			//disable this user in auth-flows (rather than delete it):
			authFlowsRepository.setDisabled( result.getKey().getName() );
		}		
	}

	@Override
	public void createBenchmarkrAccount(String traineeId, boolean isMale, Date dateOfBirth)
			throws BenchmarkrRuntimeException
	{
        log.info( "creating traineeId: " + traineeId + ", isMale? " + isMale + ", DOB=" + dateOfBirth );

        BenchmarkrAuthenticationFlowsRepositoryImpl benchmarkrAuthenticationFlowsRepository = 
				(BenchmarkrAuthenticationFlowsRepositoryImpl)authFlowsRepository;
		benchmarkrAuthenticationFlowsRepository.enrichAccount( traineeId, isMale, dateOfBirth );
	}

	@Override
	public void updateBenchmarkrAccount(String traineeId, String firstName,
			String lastName, Date dateOfBirth)
	{
        log.info( "updating traineeId: " + traineeId + ", firstName= " + firstName 
        		+ ", lastName= " + lastName+ ", DOB=" + dateOfBirth );

        BenchmarkrAuthenticationFlowsRepositoryImpl benchmarkrAuthenticationFlowsRepository = 
				(BenchmarkrAuthenticationFlowsRepositoryImpl)authFlowsRepository;
		UserDetails userDetails = benchmarkrAuthenticationFlowsRepository.loadUserByUsername( traineeId );
		BenchmarkrAuthenticationUserImpl benchmarkrAuthenticationUser = (BenchmarkrAuthenticationUserImpl) userDetails;
		BenchmarkrAuthenticationUserImpl updatedAuthUser = new BenchmarkrAuthenticationUserImpl(
				userDetails.getUsername(), 
				userDetails.getPassword(),
				userDetails.isEnabled(),
				benchmarkrAuthenticationUser.getLoginAttemptsLeft(),
				benchmarkrAuthenticationUser.getPasswordLastChangeDate(),
				firstName,
				lastName,
				userDetails.getAuthorities(),
				benchmarkrAuthenticationUser.isMale(),
				dateOfBirth,
				new Date() );

		benchmarkrAuthenticationFlowsRepository.updateUser( updatedAuthUser );
	}

	
	@Override
	public Map<String, List<TimedResult>> getRegisteredStatistics() throws BenchmarkrRuntimeException 
	{
		log.info("get stats");

		Entity statsEntity = getStatisticsEntity( STATS_KEY );

		if( statsEntity == null )
		{
			throw new BenchmarkrRuntimeException("could not find statistics Table in DB");
		}
		
		String numberOfRegisteredUsers = (String)statsEntity.getProperty(STATS__NUMBER_OF_REGISTERED_USERS);
		String numberOfRegisteredResults = (String)statsEntity.getProperty(STATS__NUMBER_OF_REGISTERED_RESULTS);

		Map<String, List<TimedResult>> retVal = new HashMap<String, List<TimedResult>>();

		List<TimedResult> usersTimedResults = parseHistoryItem( numberOfRegisteredUsers );
		retVal.put( STATS__NUMBER_OF_REGISTERED_USERS, usersTimedResults );
		List<TimedResult> numResultsTimedResults = parseHistoryItem( numberOfRegisteredResults );
		retVal.put( STATS__NUMBER_OF_REGISTERED_RESULTS, numResultsTimedResults );
		
		
		return retVal;
	}	
	
	
	@Override
	public void recordStatistics(StatisticsData statisticsData)
	{
		log.debug( "recording statistics" + statisticsData );
		
		Entity statsEntity = getStatisticsEntity( STATS_KEY );
		
		recordSingleStatistics( statsEntity, STATS__NUMBER_OF_REGISTERED_RESULTS, statisticsData.numberOfRegisteredResults );
		recordSingleStatistics( statsEntity, STATS__NUMBER_OF_REGISTERED_USERS, statisticsData.numberOfRegisteredUsers);

		datastore.put( statsEntity );	
	}


	private void recordSingleStatistics(Entity statsEntity, String columnName, int value)
	{
		String property;
		if( statsEntity.hasProperty( columnName ))
		{
			property = (String) statsEntity.getProperty( columnName );
			property += HISTORY_WORKOUT_DELIMITER + System.currentTimeMillis() + WORKOUT_PAIR_DELIMITER + value;
		}
		else
		{
			property = System.currentTimeMillis() + WORKOUT_PAIR_DELIMITER + value;
		}
		statsEntity.setProperty( columnName, property );
	}

	@Override
	public void setUserLoginSuccess(String username) throws BenchmarkrRuntimeException
	{
        BenchmarkrAuthenticationFlowsRepositoryImpl benchmarkrAuthenticationFlowsRepository = 
				(BenchmarkrAuthenticationFlowsRepositoryImpl)authFlowsRepository;
		benchmarkrAuthenticationFlowsRepository.setUserLoginSuccess( username );
	}
	
	
	public void handleNotSeenForaWhileUsers() throws BenchmarkrRuntimeException
	{
		log.info("handleNotSeenForaWhileUsers()");
		Query q = new Query( GAEAuthenticationAccountRepositoryImpl.AUTH_FLOWS_USER_DB_KIND );
		PreparedQuery pq = datastore.prepare(q);  

		Date now = new Date();
		List<TraineeMailAndName> usersToRemind = new ArrayList<TraineeMailAndName>();
		
		TraineeMailAndName traineeMailAndName;
		for (Entity entity : pq.asIterable()) 
		{
			String username = (String) entity.getKey().getName();		//the username is the key...

			try
			{
				UserDetails authFlowsUser = authFlowsRepository.loadUserByUsername( username );
				BenchmarkrAuthenticationUser authenticationUser = (BenchmarkrAuthenticationUser)authFlowsUser;
				Date lastLoginDate = authenticationUser.getLastLoginDate();

				//if last login happened more than a month ago - add this user to the list to receive email:
				if( lastLoginDate == null || DateUtils.addMonths( lastLoginDate, 1).before( now ) )
				{
					traineeMailAndName = new TraineeMailAndName( username,
							authenticationUser.getFirstName(),
							authenticationUser.getLastName() );
					//add to list:
					usersToRemind.add( traineeMailAndName );
				}
			} 
			catch (UsernameNotFoundException e)
			{
				log.error("[CANNOT HAPPEN]: user " + username + " was not found in the auth-flows DB; cannot retrieve his first+last name", e);
			}

		}	
		
		log.info( usersToRemind );
		//write the list to the DB:
		writeUsersListToDB( usersToRemind );

	}

	private void writeUsersListToDB(List<TraineeMailAndName> usersToRemind) throws BenchmarkrRuntimeException
	{
		Entity entity = getNewslettersEntity();

		if( entity == null )
		{
			throw new BenchmarkrRuntimeException("could not find newsletter Table in DB");
		}

		Text property = new Text( usersToRemind.toString() );
		entity.setProperty( "notSeenForMonth", property );
		datastore.put( entity );	
	}
	
	class TraineeMailAndName
	{
		String id;		//id == the email of the person
		String firstName;
		String lastName;
		
		TraineeMailAndName(String id, String firstName, String lastName)
		{
			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
		}
		
		@Override
		public String toString()
		{
			return id + WORKOUT_PAIR_DELIMITER +
					firstName + WORKOUT_PAIR_DELIMITER +
					lastName;
			
		}
	}

}

