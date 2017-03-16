package com.etincelles.entities;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Transient;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.multipart.MultipartFile;

import com.etincelles.entities.security.Authority;
import com.etincelles.entities.security.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class User implements UserDetails {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false, updatable = false )
    private Long              id;
    private String            firstName;
    private String            lastName;
    @Column( name = "email", nullable = false )
    private String            email;
    @Column( columnDefinition = "text" )
    private String            description;
    private String            city;
    @Transient
    private MultipartFile     picture;
    private String            password;
    private String            phone;
    private boolean           enabled          = true;
    private String            organization;
    private String            job_title;
    private String            promo_id;

    @OneToMany( mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.EAGER )
    private Set<UserCategory> categories       = new HashSet<>();

    @OneToMany( mappedBy = "role", cascade = CascadeType.ALL, fetch = FetchType.EAGER )
    @JsonIgnore
    private Set<UserRole>     userRoles        = new HashSet<>();

    public Set<UserRole> getUserRoles() {
        return userRoles;
    }

    public void setUserRoles( Set<UserRole> userRoles ) {
        this.userRoles = userRoles;
    }

    public Long getId() {
        return id;
    }

    public void setId( Long id ) {
        this.id = id;
    }

    public void setEnabled( boolean enabled ) {
        this.enabled = enabled;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( String first_name ) {
        this.firstName = first_name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName( String last_name ) {
        this.lastName = last_name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription( String description ) {
        this.description = description;
    }

    public String getCity() {
        return city;
    }

    public void setCity( String city ) {
        this.city = city;
    }

    public MultipartFile getPicture() {
        return picture;
    }

    public void setPicture( MultipartFile picture ) {
        this.picture = picture;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword( String password ) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone( String phone ) {
        this.phone = phone;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        userRoles.forEach( p -> authorities.add( new Authority( p.getRole().getName() ) ) );
        return authorities;
    }

    @Override
    public String getUsername() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization( String organization ) {
        this.organization = organization;
    }

    public String getJob_title() {
        return job_title;
    }

    public void setJob_title( String job_title ) {
        this.job_title = job_title;
    }

    public String getPromo_id() {
        return promo_id;
    }

    public void setPromo_id( String promo_id ) {
        this.promo_id = promo_id;
    }

    public Set<UserCategory> getCategories() {
        return categories;
    }

    public void setCategories( Set<UserCategory> categories ) {
        this.categories = categories;
    }

    @Override
    public boolean isAccountNonExpired() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean isEnabled() {
        // TODO Auto-generated method stub
        return enabled;
    }
}