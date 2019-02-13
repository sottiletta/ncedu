package com.edunetcracker.startreker.service;

import com.edunetcracker.startreker.dao.UserDAO;
import com.edunetcracker.startreker.domain.Role;
import com.edunetcracker.startreker.domain.User;
import com.edunetcracker.startreker.message.request.SignUpForm;
import com.edunetcracker.startreker.security.jwt.UserInformationHolder;
import com.edunetcracker.startreker.util.AuthorityUtils;
import com.edunetcracker.startreker.util.PasswordGeneratorUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserDAO userDAO;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public boolean ifUsernameExist(String username) {
        return userDAO.findByUsername(username).isPresent();
    }

    public boolean ifEmailExist(String email) {
        return userDAO.findByEmail(email).isPresent();
    }

    public User getUserByEmail(String email) {
        return userDAO.findByEmail(email).orElse(null);
    }

    public String changePasswordForUser(User user) {
        String newPassword = PasswordGeneratorUtils.generatePassword();

        user.setUserPassword(passwordEncoder.encode(newPassword));
        userDAO.save(user);

        return newPassword;
    }

    public void saveUser(User user) {
        userDAO.save(user);
    }

    public User findByName(String username) {
        return userDAO.findByUsername(username).orElse(null);
    }

    public User createUser(SignUpForm signUpForm, boolean isActivated) {
        User user = new User(signUpForm.getUsername(),
                passwordEncoder.encode(signUpForm.getPassword()),
                signUpForm.getEmail());
        user.setUserIsActivated(isActivated);
        user.setUserRoles(createRoles(signUpForm.getIsCarrier()));

        userDAO.save(user);

        return user;
    }

    public UserDetails createUserDetails(UserInformationHolder userInformationHolder) {
        if (userInformationHolder == null) {
            return null;
        }

        return new org.springframework.security.core.userdetails.User(userInformationHolder.getUsername(),
                userInformationHolder.getPassword(),
                mapRolesToAuthorities(userInformationHolder.getRoles()));
    }

    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Collection<String> roles) {
        return roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    private List<Role> createRoles(boolean isCarrier) {
        List<Role> roles = new ArrayList<>();
        roles.add(AuthorityUtils.ROLE_USER);
        if (isCarrier) {
            roles.add(AuthorityUtils.ROLE_CARRIER);
        }

        return roles;
    }
}