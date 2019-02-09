package com.edunetcracker.startreker.dao;

import com.edunetcracker.startreker.domain.Role;
import com.edunetcracker.startreker.domain.User;
import com.edunetcracker.startreker.domain.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UserDAO extends CrudDAO<User> {

    private RoleDAO roleDAO;
    private final String findByUsernameSql = "SELECT * FROM usr WHERE user_name = ?";
    private final String findAllRolesSql = "SELECT role_id FROM assigned_role WHERE user_id = ?";
    private final String removeAllUserRolesSql = "DELETE FROM assigned_role WHERE user_id = ?";
    private final String addRoleSql = "INSERT INTO assigned_role (user_id, role_id) VALUES (?, ?)";
    private final String removeRoleSql = "DELETE FROM assigned_role WHERE user_id = ? AND role_id = ?";

    @Autowired
    public UserDAO(RoleDAO roleDAO) {
        this.roleDAO = roleDAO;
    }

    @Override
    public Optional<User> find(Number id){
        Optional<User> userOpt = super.find(id);
        if(userOpt.isPresent()){
            User user = userOpt.get();
            List<Long> rows = getJdbcTemplate().queryForList(findAllRolesSql, Long.class, id);
            List<Role> roles = new ArrayList<>();
            for(Long role_id : rows){
                roles.add(roleDAO.find(role_id).orElse(null));
            }
            user.setUserRoles(roles);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<User> findByUsername(String userName){
        User user = null;
        try{
            user = (User) getJdbcTemplate().queryForObject(findByUsernameSql, new Object[]{userName}, new UserMapper());
        }catch (ClassCastException e){
            return Optional.empty();
        }
        if(user != null){
            List<Long> rows = getJdbcTemplate().queryForList(findAllRolesSql, Long.class, user.getUserId());
            List<Role> roles = new ArrayList<>();
            for(Long role_id : rows){
                roles.add(roleDAO.find(role_id).orElse(null));
            }
            user.setUserRoles(roles);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public void save(User user){
        super.save(user);
        List<Long> dbRoleIds = getJdbcTemplate().queryForList(findAllRolesSql, Long.class, user.getUserId());
        List<Long> userRoleIds = user.getUserRoles()
                .stream()
                .map(Role::getRoleId)
                .collect(Collectors.toList());
        for(Long role_id : userRoleIds){
            if(!dbRoleIds.contains(role_id)) {
                getJdbcTemplate().update(addRoleSql, user.getUserId(), role_id);
            }
        }
        for(Long db_role : dbRoleIds){
            if(!userRoleIds.contains(db_role)){
                getJdbcTemplate().update(removeRoleSql, user.getUserId(), db_role);
            }
        }
    }

    @Override
    public void delete(User user){
        getJdbcTemplate().update(removeAllUserRolesSql, user.getUserId());
        super.delete(user);
    }

}