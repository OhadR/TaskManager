package com.ohadr.cbenchmarkr;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import com.google.appengine.api.datastore.*;
import com.ohadr.cbenchmarkr.core.BenchmarkrUserDetailsImpl;
import com.ohadr.cbenchmarkr.interfaces.BenchmarkrUserDetails;


@Component
public class GAEAccountRepositoryImpl implements UserDetailsManager 
{
	private static final String PASSWORD_PROP_NAME = "password";
	private static final String FIRST_NAME_PROP_NAME = "firstName";
	private static final String LAST_NAME_PROP_NAME = "lastName";
	private static final String AUTHORITIES_PROP_NAME = "authorities";
	private static final String IS_MALE_PROP_NAME = "isMale";
	private static final String LAST_LOGIN_PROP_NAME = "lastLogin";

	public static final String AUTH_FLOWS_USER_DB_KIND = "authentication-flows-user";


	private static Logger log = Logger.getLogger(GAEAccountRepositoryImpl.class);

	//allow classes that inherit this class to use datastore elegantly
	protected DatastoreService datastore;
	
	public GAEAccountRepositoryImpl()
	{
		datastore = DatastoreServiceFactory.getDatastoreService();
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException
	{
		//issue #34: if username is null, login fails and then we try to AuthenticationFlowsProcessorImpl.setLoginFailureForUser()
		//that causes IllegalArgumentException.
		if( username == null || username.isEmpty() )
		{
			log.error("name cannot be null or empty");
			throw new UsernameNotFoundException( "name cannot be null or empty" );
		}
		
		Key userKey = KeyFactory.createKey(AUTH_FLOWS_USER_DB_KIND, username);
		Entity entity;
		try 
		{
			entity = datastore.get(userKey);
			log.debug("got entity of " + username + ": " + entity);
		} 
		catch (EntityNotFoundException e) 
		{
			log.error("entity of " + username + " not found");
			throw new UsernameNotFoundException(username, e);
		}
		

		String firstName = (String)entity.getProperty(FIRST_NAME_PROP_NAME);
		String lastName = (String)entity.getProperty(LAST_NAME_PROP_NAME);

		String roleName = (String)entity.getProperty(AUTHORITIES_PROP_NAME);
		GrantedAuthority userAuth = new SimpleGrantedAuthority(roleName);
		Collection<GrantedAuthority>  authSet = new HashSet<GrantedAuthority>();
		authSet.add(userAuth);
		
		log.debug( "$$$$$ user " + username + ", authSet= " + authSet );
		
		return new BenchmarkrUserDetailsImpl(
						username, 
						firstName,
						lastName,
						authSet,
						false,
						new Date());
		
	}

	@Override
	public void createUser(UserDetails user) 
	{
		BenchmarkrUserDetails authUser = (BenchmarkrUserDetails) user;

		Entity dbUser = new Entity(AUTH_FLOWS_USER_DB_KIND, user.getUsername());		//the username is the key

		dbUser.setProperty("username", user.getUsername());
		dbUser.setProperty(PASSWORD_PROP_NAME, user.getPassword());

		dbUser.setProperty( FIRST_NAME_PROP_NAME, authUser.getFirstName() );
		dbUser.setProperty( LAST_NAME_PROP_NAME, authUser.getLastName() );

		dbUser.setProperty(AUTHORITIES_PROP_NAME, "ROLE_USER" );

		dbUser.setProperty( IS_MALE_PROP_NAME, authUser.isMale() );

		datastore.put(dbUser);	
	}

	@Override
	public void updateUser(UserDetails user) 
	{
		createUser( user );
	}

	@Override
	public void deleteUser(String username) 
	{
		Key userKey = KeyFactory.createKey(AUTH_FLOWS_USER_DB_KIND, username);
		datastore.delete(userKey);
		
	}

	@Override
	public void changePassword(String oldPassword, String newPassword) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean userExists(String username) {
		// TODO Auto-generated method stub
		return false;
	}

	

//TODO	@Override
	public void setAuthority(String username, String authority)
	{
		Key userKey = KeyFactory.createKey(AUTH_FLOWS_USER_DB_KIND, username);
		Entity entity;
		try 
		{
			entity = datastore.get(userKey);
			log.debug("got entity of " + username + ": " + entity);
		} 
		catch (EntityNotFoundException e) 
		{
			log.error("entity of " + username + " not found");
			throw new NoSuchElementException(e.getMessage());
		}
		
		entity.setProperty(AUTHORITIES_PROP_NAME, authority );
		datastore.put( entity );	
	}

	public void setUserLoginSuccess(String username)
	{
		Key userKey = KeyFactory.createKey(AUTH_FLOWS_USER_DB_KIND, username);
		Entity entity;
		try 
		{
			entity = datastore.get(userKey);
			log.debug("got entity of " + username + ": " + entity);
		} 
		catch (EntityNotFoundException e) 
		{
			log.error("entity of " + username + " not found");
			throw new NoSuchElementException(e.getMessage());
		}
		
		entity.setProperty(LAST_LOGIN_PROP_NAME, new Date() );
		datastore.put( entity );	
	}
}
