/*
 mycloudportal - Self Service Portal for the cloud.
 Copyright (C) 2012-2013 Mycloudportal Technologies Pvt Ltd

 This file is part of mycloudportal.

 mycloudportal is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 mycloudportal is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with mycloudportal.  If not, see <http://www.gnu.org/licenses/>.
 */
package in.mycp.remote;

import in.mycp.domain.Company;
import in.mycp.domain.Department;
import in.mycp.domain.Project;
import in.mycp.domain.Role;
import in.mycp.domain.User;
import in.mycp.utils.Commons;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.directwebremoting.annotations.RemoteMethod;
import org.directwebremoting.annotations.RemoteProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.encoding.ShaPasswordEncoder;

/**
 * 
 * @author Charudath Doddanakatte
 * @author cgowdas@gmail.com
 * 
 */

@RemoteProxy(name = "RealmService")
public class RealmService {

	private static final Logger log = Logger.getLogger(RealmService.class
			.getName());

	SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-hh.mm.ss");
	@Autowired
	AccountLogService accountLogService;

	@Autowired
	ShaPasswordEncoder passwordEncoder;

	@RemoteMethod
	public boolean emailExists(String email) {
		try {
			User user = User.findUsersByEmailEquals(email).getSingleResult();

			if (user != null) {
				return true;
			} else {
				return false;
			}

		} catch (Exception e) {
			log.error(e.getMessage());
			//e.printStackTrace();
			return false;
		}
	}

	@RemoteMethod
	public User saveOrUpdate(User instance) {
		try {
			if (instance != null && StringUtils.isBlank(instance.getPassword())) {
				throw new Exception("Password cannot be empty");
			}
			
			int quotaInDB = 0;
			int quotaFromUI = 0;
			boolean saveLog=false;
			
			//to update the user projects
			Set<Project> stProjects2Save = new HashSet<Project>();
			Set<Project> stProjects = instance.getProjects();
			for (Iterator iterator = stProjects.iterator(); iterator.hasNext();) {
				Project project = (Project) iterator.next();
				project = Project.findProject(project.getId());
				project.getUsers().add(instance);
				stProjects2Save.add(project);
			}
			User localUser = User.findUser(instance.getId());
			//if this method call is for new user
			if (localUser == null) {
				//then , set the registered date and save the encoded password
				instance.setRegistereddate(new Date());
				instance.setPassword(passwordEncoder.encodePassword(instance.getPassword(), instance.getEmail()));
			} else {
				
				 quotaInDB = localUser.getQuota();
				 quotaFromUI = instance.getQuota();
				 saveLog = true;
				 
				if(localUser.getRegistereddate() != null){
					instance.setRegistereddate(localUser.getRegistereddate());
				}else{
					instance.setRegistereddate(new Date());
				}
				String encodedPassword = passwordEncoder.encodePassword(instance.getPassword(), instance.getEmail());
				
				/*System.out.println(" encodedPassword =  "+encodedPassword);
				System.out.println(" localUser.getPassword() =  "+localUser.getPassword());*/
				
				instance.setLoggedInDate(localUser.getLoggedInDate());
				if (!localUser.getPassword().equals(instance.getPassword())) {
					instance.setPassword(encodedPassword);
				}else{
					instance.setPassword(localUser.getPassword());
				}
			}
			//Gangu: why do you do this? - Charu
			if(localUser != null){
				localUser.clear();	
			}
			//if you dont add this, no user_project entries are made, along with project master data entries are made ""
			instance.setProjects(stProjects2Save);
			//now, merge the instance
			instance = instance.merge();
			
			//To avoid the following error, merging the object above.
			//	object references an unsaved transient instance - save the transient instance before flushing: in.mycp.domain.User; 
			// nested exception is java.lang.IllegalStateException: org.hibernate.TransientObjectException: object references an unsaved transient instance - 
			// save the transient instance before flushing: in.mycp.domain.User
			accountLogService.saveLog("User " + instance.getEmail()+" created/updating, ",
					Commons.task_name.USER.name(),
					Commons.task_status.SUCCESS.ordinal(),
					Commons.getCurrentUser().getEmail());
				
				if(saveLog && quotaInDB != quotaFromUI ){
					accountLogService.saveLogAndSendMail("User '"+instance.getEmail()+"' Quota updated from '"+quotaInDB+"' to '"+quotaFromUI+"'", "User '"+instance.getEmail()+"' Quota updated", 1, instance.getEmail());
				}
			
			return instance.merge();
		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
			accountLogService.saveLog("Error in User " + instance.getEmail()+" creation/updating, "+e.getMessage(),
					Commons.task_name.USER.name(),
					Commons.task_status.FAIL.ordinal(),
					Commons.getCurrentUser().getEmail());
		}
		return null;
	}// end of saveOrUpdate(User

	@RemoteMethod
	public void remove(int id) {
		try {
			User u = User.findUser(id);
			u.setActive(false);
			u.merge();
			//u.remove();
			
			accountLogService.saveLog("User " + u.getEmail()+" removed, ",
					Commons.task_name.USER.name(),
					Commons.task_status.SUCCESS.ordinal(),
					Commons.getCurrentUser().getEmail());
		} catch (Exception e) {
			log.error(e.getMessage());//e.printStackTrace();
			accountLogService.saveLog("Error in User " + User.findUser(id).getEmail()+" removal, "+e.getMessage(),
					Commons.task_name.USER.name(),
					Commons.task_status.FAIL.ordinal(),
					Commons.getCurrentUser().getEmail());
		}
	}// end of method remove(int id

	@RemoteMethod
	public User findById(int id) {
		try {
			User user = User.findUser(id);
			user.getProjects().size();//without this user page will not work properly
			return user;
		} catch (Exception e) {
			log.error(e.getMessage());// e.printStackTrace();
		}
		return null;
	}// end of method findById(int id

	@RemoteMethod
	public List<User> findAll() {
		try {

			if (Commons.getCurrentUser().getRole().getName()
					.equals(Commons.ROLE.ROLE_SUPERADMIN + "")) {
				return User.findAllUsers();
			} else {
				List<User> list = User.findAllUsers();
				for (Iterator iterator = list.iterator(); iterator.hasNext();) {
					User user = (User) iterator.next();
					user.getProjects().size();//without this user page will not work properly
				}
				return User.findUsersByCompany(
						Company.findCompany(Commons.getCurrentSession()
								.getCompanyId())).getResultList();
			}
		} catch (Exception e) {
			log.error(e.getMessage());// e.printStackTrace();
		}
		return null;
	}// end of method findAll

	@RemoteMethod
	public List<Role> findAllRoles() {
		try {
			// System.out.println(" = "+Role.findAllRoles().size());
			return Role.findAllRoles();
		} catch (Exception e) {
			log.error(e.getMessage());// e.printStackTrace();
		}
		return null;
	}// end of method findAll
	
	@RemoteMethod
	public List<User> findUsersByEmailEquals(String email){
		return User.findUsersByEmailEquals(email).getResultList();
	}

	@RemoteMethod
	public List<User> findUsersByDepartment(Department department ){
		return User.findUsersByDepartment(department).getResultList();
	}
}// end of class UserController

