package com.etincelles.controller;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.etincelles.entities.Message;
import com.etincelles.entities.PasswordResetToken;
import com.etincelles.entities.Skill;
import com.etincelles.entities.User;
import com.etincelles.entities.UserSkill;
import com.etincelles.repository.SkillRespository;
import com.etincelles.service.CustomUserService;
import com.etincelles.service.MessageService;
import com.etincelles.service.UserService;
import com.etincelles.service.impl.UserSecurityService;
import com.etincelles.utility.MailConstructor;
import com.etincelles.utility.SecurityUtility;

@Controller
public class HomeController implements ErrorController {
    @Autowired
    private JavaMailSender      mailSender;

    @Autowired
    private MailConstructor     mailConstructor;

    @Autowired
    private UserService         userService;

    @Autowired
    private MessageService      messageService;

    @Autowired
    private CustomUserService   customUserService;

    @Autowired
    private SkillRespository    skillRepo;

    @Autowired
    private UserSecurityService userSecurityService;

    @RequestMapping( "/" )
    public String index() {
        return "index";
    }

    @RequestMapping( "/login" )
    public String login( Model model ) {
        model.addAttribute( "classActiveLogin", true );
        return "myAccount";
    }

    @RequestMapping( "/forgetPassword" )
    public String forgetPassword(
            HttpServletRequest request,
            @ModelAttribute( "email" ) String email,
            Model model ) {

        model.addAttribute( "classActiveForgetPassword", true );

        User user = userService.findByEmail( email );

        if ( user == null ) {
            model.addAttribute( "emailNotExist", true );
            return "myAccount";
        }

        String password = SecurityUtility.randomPassword();

        String encryptedPassword = SecurityUtility.passwordEncoder().encode( password );
        user.setPassword( encryptedPassword );

        userService.save( user );

        String token = UUID.randomUUID().toString();
        userService.createPasswordResetTokenForUser( user, token );

        String appUrl = "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();

        SimpleMailMessage newEmail = mailConstructor.constructResetTokenEmail( appUrl, request.getLocale(), token, user,
                password );

        mailSender.send( newEmail );

        model.addAttribute( "forgetPasswordEmailSent", "true" );

        return "myAccount";
    }

    @RequestMapping( "/updateUser" )
    public String newUser( Locale locale, @RequestParam( "token" ) String token, Model model ) {
        PasswordResetToken passToken = userService.getPasswordResetToken( token );

        if ( passToken == null ) {
            String message = "Invalid Token.";
            model.addAttribute( "message", message );
            return "redirect:/badRequestPage";
        }

        User user = passToken.getUser();
        String email = user.getEmail();
        UserDetails userDetails = userSecurityService.loadUserByUsername( email );

        Authentication authentication = new UsernamePasswordAuthenticationToken( userDetails, userDetails.getPassword(),
                userDetails.getAuthorities() );
        SecurityContextHolder.getContext().setAuthentication( authentication );

        List<String> skills = new ArrayList<>();
        for ( UserSkill userSkill : user.getUserSkills() ) {
            skills.add( userSkill.getSkill().getName() );
        }

        if ( skills.size() != 0 ) {
            model.addAttribute( "skills", skills );
        }
        model.addAttribute( "classActiveEdit", true );
        model.addAttribute( "user", user );
        return "myProfile";
    }

    @RequestMapping( "/updateUserInfo" )
    public String updateGet( Model model, Principal principal ) {
        User activeUser = (User) ( (Authentication) principal ).getPrincipal();
        User user = userService.findByEmail( activeUser.getEmail() );
        List<String> skills = new ArrayList<>();
        for ( UserSkill userSkill : user.getUserSkills() ) {
            skills.add( userSkill.getSkill().getName() );
        }

        if ( skills.size() != 0 ) {
            model.addAttribute( "skills", skills );
        }
        model.addAttribute( "user", user );
        model.addAttribute( "classActiveEdit", true );
        return "myProfile";
    }

    @RequestMapping( value = "/updateUserInfo", method = RequestMethod.POST )
    public String updateUserInfo( @ModelAttribute( "user" ) User user, HttpServletRequest request,
            @ModelAttribute( "newPassword" ) String newPassword, Model model ) throws Exception {

        User currentUser = userService.findById( user.getId() );
        if ( currentUser == null ) {
            throw new Exception( "User not found" );
        }

        /* check email already exists */
        if ( userService.findByEmail( user.getEmail() ) != null ) {
            if ( userService.findByEmail( user.getEmail() ).getId() != currentUser.getId() ) {
                model.addAttribute( "emailExists", true );
                return "myProfile";
            }
        }

        MultipartFile picture = user.getPicture();
        if ( !( picture.isEmpty() ) ) {
            try {
                byte[] bytes = picture.getBytes();
                String name = user.getId() + ".png";
                if ( Files.exists( Paths.get( "/home/clem/etincelles/images/user/" + name ) ) ) {
                    Files.delete( Paths.get( "/home/clem/etincelles/images/user/" + name ) );
                }
                BufferedOutputStream stream = new BufferedOutputStream(
                        new FileOutputStream( new File( "/home/clem/etincelles/images/user/" + name ) ) );
                stream.write( bytes );
                stream.close();
                currentUser.setHasPicture( true );
            } catch ( Exception e ) {
                System.out.println( "Erreur ligne 198" );
                e.printStackTrace();
            }
        }

        BCryptPasswordEncoder passwordEncoder = SecurityUtility.passwordEncoder();
        String dbPassword = currentUser.getPassword();

        // verify current password
        if ( !( passwordEncoder.matches( user.getPassword(), dbPassword ) ) ) {
            model.addAttribute( "incorrectPassword", true );
            return "myProfile";
        }

        // update password
        if ( newPassword != null && !newPassword.isEmpty() && !newPassword.equals( "" ) ) {
            if ( passwordEncoder.matches( user.getPassword(), dbPassword ) ) {
                currentUser.setPassword( passwordEncoder.encode( newPassword ) );
            } else {
                model.addAttribute( "incorrectPassword", true );
                return "myProfile";
            }
        }
        if ( user.isNoContact() ) {
            currentUser.setNoContact( user.isNoContact() );
        }

        currentUser.setFirstName( user.getFirstName() );
        currentUser.setLastName( user.getLastName() );
        currentUser.setEmail( user.getEmail() );
        currentUser.setDescription( user.getDescription() );
        currentUser.setCity( user.getCity() );
        currentUser.setCategory( user.getCategory() );
        currentUser.setFacebook( user.getFacebook() );
        currentUser.setTwitter( user.getTwitter() );
        currentUser.setLinkedin( user.getLinkedin() );
        currentUser.setPromo( user.getPromo() );
        currentUser.setType( user.getType() );
        currentUser.setSector( user.getSector() );

        Set<UserSkill> userSkills = new HashSet<>();
        if ( request.getParameterMap().containsKey( "skills" ) ) {
            String[] skills = request.getParameterMap().get( "skills" );
            if ( skills.length > 4 ) {
                model.addAttribute( "incorrectSkills", true );
                return "myProfile";
            }
            // Delete existing UserSkills and replace them with the new list
            for ( String skillString : skills ) {
                for ( UserSkill userSkill : currentUser.getUserSkills() ) {
                    customUserService
                            .executeStringQuery(
                                    "DELETE FROM user_skill WHERE user_skill_id= " + userSkill.getUserSkillId() );
                }
                // If skill does not exist, create it
                Skill skill = skillRepo.findByname( skillString );
                if ( skill == null ) {
                    skill = new Skill();
                    skill.setName( skillString );
                    skillRepo.save( skill );
                }
                userSkills.add( new UserSkill( user, skill ) );
            }
        }
        currentUser.setUserSkills( userSkills );

        List<String> skills = new ArrayList<>();
        for ( UserSkill userSkill : currentUser.getUserSkills() ) {
            skills.add( userSkill.getSkill().getName() );
        }

        if ( skills.size() != 0 ) {
            model.addAttribute( "skills", skills );
        }

        userService.save( currentUser );

        model.addAttribute( "updateSuccess", true );
        model.addAttribute( "user", currentUser );
        model.addAttribute( "classActiveEdit", true );

        UserDetails userDetails = userSecurityService.loadUserByUsername( currentUser.getEmail() );

        Authentication authentication = new UsernamePasswordAuthenticationToken( userDetails, userDetails.getPassword(),
                userDetails.getAuthorities() );

        SecurityContextHolder.getContext().setAuthentication( authentication );

        return "myProfile";

    }

    @RequestMapping( "/directory" )
    public String directory( Model model ) {
        List<User> userList;
        userList = userService.findAll();
        List<User> users = new ArrayList<>();
        for ( User user : userList ) {
            if ( user.getEnabled() ) {
                users.add( user );
            }
        }

        List<Skill> skills = (List<Skill>) skillRepo.findAll();
        List<String> skillList = new ArrayList<>();
        for ( Skill skill : skills ) {
            skillList.add( skill.getName() );
        }

        model.addAttribute( "skillList", skillList );
        model.addAttribute( "directory", true );
        model.addAttribute( "userList", users );
        return "directory";
    }

    @RequestMapping( "/userDetail" )
    public String UserDetail( @RequestParam( "id" ) Long id, Model model ) {
        User user = userService.findById( id );
        model.addAttribute( "user", user );
        return "userDetail";
    }

    @RequestMapping( "/myProfile" )
    public String myProfile( Model model, Principal principal ) {
        User activeUser = (User) ( (Authentication) principal ).getPrincipal();
        User user = userService.findByEmail( activeUser.getEmail() );
        List<String> skills = new ArrayList<>();
        for ( UserSkill userSkill : user.getUserSkills() ) {
            skills.add( userSkill.getSkill().getName() );
        }

        if ( skills.size() != 0 ) {
            model.addAttribute( "skills", skills );
        }
        model.addAttribute( "user", user );
        model.addAttribute( "classActiveEdit", true );
        return "myProfile";
    }

    @RequestMapping( "/calendar" )
    public String calendar( Model model ) {
        return "calendar";
    }

    @RequestMapping( "/news" )
    public String news( Model model ) {
        List<Message> messagesList;
        messagesList = messageService.findAll();
        model.addAttribute( "messageList", messagesList );
        return "news";
    }

    @RequestMapping( "/post" )
    public String post( Model model, @RequestParam( "id" ) Long id ) {
        Message message = messageService.findById( id );
        model.addAttribute( "message", message );
        return "post";
    }

    @RequestMapping( "/searchUser" )
    public String searchBook(
            @ModelAttribute( "keyword" ) String keyword,
            Principal principal, Model model ) {

        List<User> userList = userService.blurrySearch( keyword );

        if ( userList.isEmpty() ) {
            model.addAttribute( "emptyList", true );
            model.addAttribute( "directory", true );
            return "directory";
        }

        List<Skill> skills = (List<Skill>) skillRepo.findAll();
        List<String> skillList = new ArrayList<>();
        for ( Skill skill : skills ) {
            skillList.add( skill.getName() );
        }

        model.addAttribute( "skillList", skillList );

        model.addAttribute( "userList", userList );
        model.addAttribute( "directory", true );
        return "directory";
    }

    @RequestMapping( value = "/directorySearch", method = RequestMethod.POST )
    public String directorySearchPost( Model model, HttpServletRequest request ) {

        String queryString = "SELECT distinct id from user, user_skill where";
        String search = "Votre recherche :";
        boolean needOr = false;
        boolean empty = true;

        if ( request.getParameterMap().containsKey( "skills" ) ) {
            String[] skills = request.getParameterValues( "skills" );
            queryString = "SELECT distinct id from user, user_skill where user.id = user_skill.user_id and";
            for ( int i = 0; i < skills.length; i++ ) {
                search += " " + skills[i];
                if ( i > 0 ) {
                    queryString += " or ";
                }
                Skill skill = skillRepo.findByname( skills[i] );
                queryString += " user_skill.skill_id = " + skill.getSkillId();
                if ( needOr == false ) {
                    needOr = true;
                }
            }
        }

        if ( request.getParameterMap().containsKey( "sectors" ) ) {
            String[] sectors = request.getParameterValues( "sectors" );
            if ( needOr ) {
                queryString += " or ";
            }
            for ( int i = 0; i < sectors.length; i++ ) {
                search += " " + sectors[i];
                if ( i > 0 ) {
                    queryString += " or ";
                }
                queryString += " user.sector = " + "\'" + sectors[i] + "\'";
                if ( needOr == false ) {
                    needOr = true;
                }
            }
        }

        if ( request.getParameterMap().containsKey( "categories" ) ) {
            String[] categories = request.getParameterValues( "categories" );
            if ( needOr ) {
                queryString += " or ";
            }
            for ( int i = 0; i < categories.length; i++ ) {
                search += " " + categories[i];
                if ( i > 0 ) {
                    queryString += " or ";
                }
                queryString += " user.category = " + "\'" + categories[i] + "\'";
                if ( needOr == false ) {
                    needOr = true;
                }
            }
        }

        if ( request.getParameterMap().containsKey( "cities" ) ) {
            String[] cities = request.getParameterValues( "cities" );
            if ( needOr ) {
                queryString += " or ";
            }
            for ( int i = 0; i < cities.length; i++ ) {
                search += " " + cities[i];
                if ( i > 0 ) {
                    queryString += " or ";
                }
                queryString += " user.city = " + "\'" + cities[i] + "\'";
                if ( needOr == false ) {
                    needOr = true;
                }
            }
        }
        System.out.println( queryString );

        List<User> userList = null;
        userList = customUserService.searchQuery( queryString );
        if ( !userList.isEmpty() ) {
            empty = false;
        }

        List<Skill> skills = (List<Skill>) skillRepo.findAll();
        List<String> skillList = new ArrayList<>();
        for ( Skill skill : skills ) {
            skillList.add( skill.getName() );
        }

        model.addAttribute( "skillList", skillList );
        model.addAttribute( "listEmpty", empty );
        model.addAttribute( "userList", userList );
        model.addAttribute( "searchString", search );
        model.addAttribute( "directory", true );
        return "directory";
    }

    @RequestMapping( "/directorySearch" )
    public String directorySearch() {
        return "redirect:/directory";
    }

    @RequestMapping( "/error" )
    public String error() {
        return "badRequestPage";
    }

    @Override
    public String getErrorPath() {
        return "/error";
    }

    @RequestMapping( value = "/contact", method = RequestMethod.POST )
    public String contact( Model model, @RequestParam( "name" ) String name, @RequestParam( "email" ) String email,
            @RequestParam( "content" ) String text, @RequestParam( "userEmail" ) String userEmail ) {
        SimpleMailMessage newEmail = mailConstructor.constructContactEmail( name, email, text, userEmail );
        mailSender.send( newEmail );
        model.addAttribute( "emailSent", true );
        return "confirmSend";
    }

    @RequestMapping( "/aboutUs" )
    public String aboutUs() {
        return "aboutUs";
    }

}
