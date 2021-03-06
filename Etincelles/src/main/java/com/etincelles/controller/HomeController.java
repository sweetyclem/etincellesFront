package com.etincelles.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import com.etincelles.entities.PasswordResetToken;
import com.etincelles.entities.Post;
import com.etincelles.entities.Skill;
import com.etincelles.entities.User;
import com.etincelles.repository.SkillRespository;
import com.etincelles.service.CustomUserService;
import com.etincelles.service.PostService;
import com.etincelles.service.UserService;
import com.etincelles.service.impl.UserSecurityService;
import com.etincelles.utility.FileUtility;
import com.etincelles.utility.MailConstructor;
import com.etincelles.utility.PageWrapper;
import com.etincelles.utility.SecurityUtility;

@Controller
public class HomeController {
    @Autowired
    private JavaMailSender      mailSender;

    @Autowired
    private MailConstructor     mailConstructor;

    @Autowired
    private UserService         userService;

    @Autowired
    private PostService         postService;

    @Autowired
    private CustomUserService   customUserService;

    @Autowired
    private SkillRespository    skillRepo;

    @Autowired
    private UserSecurityService userSecurityService;

    @Autowired
    private FileUtility         fileUtility;

    @Autowired
    private SecurityUtility     securityUtility;

    @RequestMapping( "/" )
    public String index() {
        return "index";
    }

    @RequestMapping( "/login" )
    public String login( final Model model ) {
        model.addAttribute( "classActiveLogin", true );
        return "myAccount";
    }

    @RequestMapping( "/forgetPassword" )
    public String forgetPassword( final HttpServletRequest request, @ModelAttribute( "email" ) final String email,
            final Model model ) {

        model.addAttribute( "classActiveForgetPassword", true );

        final User user = this.userService.findByEmail( email );

        if ( user == null ) {
            model.addAttribute( "emailNotExist", true );
            return "myAccount";
        }

        final String password = securityUtility.randomPassword();

        final String encryptedPassword = securityUtility.passwordEncoder().encode( password );
        user.setPassword( encryptedPassword );

        this.userService.save( user );

        // String token = UUID.randomUUID().toString();
        // userService.createPasswordResetTokenForUser( user, token );

        // String appUrl = "http://" + request.getServerName() + ":" +
        // request.getServerPort() + request.getContextPath();

        final SimpleMailMessage newEmail = this.mailConstructor.constructResetPasswordEmail( request.getLocale(), user,
                password );

        this.mailSender.send( newEmail );

        model.addAttribute( "forgetPasswordEmailSent", "true" );

        return "myAccount";
    }

    @RequestMapping( "/updateUser" )
    public String newUser( final Locale locale, @RequestParam( "token" ) final String token, final Model model ) {
        final PasswordResetToken passToken = this.userService.getPasswordResetToken( token );

        if ( passToken == null ) {
            final String post = "Invalid Token.";
            model.addAttribute( "post", post );
            return "redirect:/badRequestPage";
        }

        final User user = passToken.getUser();
        final String email = user.getEmail();
        final UserDetails userDetails = this.userSecurityService.loadUserByUsername( email );

        final Authentication authentication = new UsernamePasswordAuthenticationToken( userDetails,
                userDetails.getPassword(), userDetails.getAuthorities() );
        SecurityContextHolder.getContext().setAuthentication( authentication );

        final List<String> skills = new ArrayList<>();
        for ( final Skill skill : user.getSkills() ) {
            skills.add( skill.getName() );
        }

        if ( skills.size() != 0 ) {
            model.addAttribute( "skills", skills );
        }
        model.addAttribute( "classActiveEdit", true );
        model.addAttribute( "user", user );
        return "myProfile";
    }

    @RequestMapping( "/updateUserInfo" )
    public String updateGet( final Model model, final Principal principal ) {
        if ( null != principal ) {
            final User activeUser = (User) ( (Authentication) principal ).getPrincipal();
            final User user = this.userService.findByEmail( activeUser.getEmail() );

            final List<String> skills = new ArrayList<>();
            for ( final Skill skill : user.getSkills() ) {
                skills.add( skill.getName() );
            }

            if ( skills.size() != 0 ) {
                model.addAttribute( "skills", skills );
            }
            model.addAttribute( "user", user );
            model.addAttribute( "classActiveEdit", true );
            return "myProfile";
        }
        return "redirect:/login";
    }

    @RequestMapping( value = "/updateUserInfo", method = RequestMethod.POST )
    public String updateUserInfo( @ModelAttribute( "user" ) final User user, final HttpServletRequest request,
            @ModelAttribute( "newPassword" ) final String newPassword, final Model model )
            throws Exception {

        final User currentUser = this.userService.findById( user.getId() );
        if ( currentUser == null ) {
            throw new Exception( "User not found" );
        }

        /* check email already exists */
        if ( this.userService.findByEmail( user.getEmail() ) != null ) {
            if ( this.userService.findByEmail( user.getEmail() ).getId() != currentUser.getId() ) {
                model.addAttribute( "emailExists", true );
                return "myProfile";
            }
        }

        final MultipartFile picture = user.getPicture();
        if ( !picture.isEmpty() ) {
            try {
                // Crop the image (uploadfile is an object of type
                // MultipartFile)
                final BufferedImage croppedImage = this.fileUtility.cropImageSquare( picture.getBytes() );

                final String name = user.getId() + ".png";
                if ( Files.exists( Paths.get( "/home/clem/etincelles/user_resources/user/" + name ) ) ) {
                    Files.delete( Paths.get( "/home/clem/etincelles/user_resources/user/" + name ) );
                }
                // Save the file locally
                final File outputfile = new File( "/home/clem/etincelles/user_resources/user/" + name );
                ImageIO.write( croppedImage, "png", outputfile );
                currentUser.setHasPicture( true );
            } catch ( final Exception e ) {
                System.out.println( "Erreur ligne 198" );
                e.printStackTrace();
            }
        }

        final BCryptPasswordEncoder passwordEncoder = securityUtility.passwordEncoder();

        // update password
        if ( newPassword != null && !newPassword.isEmpty() && !newPassword.equals( "" ) ) {
            currentUser.setPassword( passwordEncoder.encode( newPassword ) );
        }

        currentUser.setNoContact( user.isNoContact() );
        currentUser.setFirstName( user.getFirstName() );
        currentUser.setLastName( user.getLastName() );
        currentUser.setEmail( user.getEmail() );
        currentUser.setDescription( user.getDescription() );
        currentUser.setCity( user.getCity() );
        currentUser.setFacebook( user.getFacebook() );
        currentUser.setTwitter( user.getTwitter() );
        currentUser.setLinkedin( user.getLinkedin() );
        currentUser.setWebsite( user.getWebsite() );
        currentUser.setPromo( user.getPromo() );
        currentUser.setSector( user.getSector() );
        currentUser.setCurrentPosition( user.getCurrentPosition() );

        if ( request.getParameterMap().containsKey( "skillNames" ) ) {
            final String[] skills = request.getParameterMap().get( "skillNames" );
            if ( skills.length > 4 ) {
                model.addAttribute( "incorrectSkills", true );
                return "myProfile";
            }
            // Delete existing UserSkills and replace them with the new list
            final List<Skill> skillList = new ArrayList<>();
            for ( final String skillString : skills ) {
                // If skill does not exist, create it
                Skill skill = this.skillRepo.findByname( skillString );
                if ( skill == null ) {
                    skill = new Skill();
                    skill.setName( skillString );
                    this.skillRepo.save( skill );
                }
                skillList.add( skill );
            }
            currentUser.setSkills( skillList );
        }

        final List<String> skills = new ArrayList<>();
        for ( final Skill skill : currentUser.getSkills() ) {
            skills.add( skill.getName() );
        }

        if ( skills.size() != 0 ) {
            model.addAttribute( "skills", skills );
        }

        this.userService.save( currentUser );

        model.addAttribute( "updateSuccess", true );
        model.addAttribute( "user", currentUser );
        model.addAttribute( "classActiveEdit", true );

        final UserDetails userDetails = this.userSecurityService.loadUserByUsername( currentUser.getEmail() );

        final Authentication authentication = new UsernamePasswordAuthenticationToken( userDetails,
                userDetails.getPassword(), userDetails.getAuthorities() );

        SecurityContextHolder.getContext().setAuthentication( authentication );

        return "myProfile";

    }

    @RequestMapping( "/directory" )
    public String directory( final Model model, final HttpSession session,
            @PageableDefault( value = 28 ) final Pageable pageable ) {
        final Page<User> userPage = this.userService.findAll( pageable );
        final PageWrapper<User> page = new PageWrapper<User>( userPage, "/directory" );
        model.addAttribute( "userList", page.getContent() );
        model.addAttribute( "page", page );

        final List<Skill> skills = this.skillRepo.findAll();
        final List<String> skillList = new ArrayList<>();
        for ( final Skill skill : skills ) {
            skillList.add( skill.getName() );
        }

        final List<String> sectorList = userService.getSectors();

        session.setAttribute( "skillList", skillList );
        model.addAttribute( "skillList", skillList );
        session.setAttribute( "sectors", sectorList );
        model.addAttribute( "sectors", sectorList );

        model.addAttribute( "directory", true );
        return "directory";
    }

    @RequestMapping( "/userDetail" )
    public String UserDetail( @RequestParam( value = "id", required = false ) Long id, final Model model,
            final HttpSession httpSession ) {
        if ( id == null ) {
            id = (Long) httpSession.getAttribute( "id" );
            model.addAttribute( "emailSent", true );
        }
        final User user = this.userService.findById( id );
        model.addAttribute( "user", user );
        return "userDetail";
    }

    @RequestMapping( "/myProfile" )
    public String myProfile( final Model model, final Principal principal ) {
        if ( null != principal ) {
            final User activeUser = (User) ( (Authentication) principal ).getPrincipal();
            final User user = this.userService.findByEmail( activeUser.getEmail() );
            final List<String> skills = new ArrayList<>();
            for ( final Skill skill : user.getSkills() ) {
                skills.add( skill.getName() );
            }

            if ( skills.size() != 0 ) {
                model.addAttribute( "skills", skills );
            }
            model.addAttribute( "user", user );
            model.addAttribute( "classActiveEdit", true );
            return "myProfile";
        }
        return "redirect:/login";
    }

    @RequestMapping( "/calendar" )
    public String calendar( final Model model ) {
        return "calendar";
    }

    @RequestMapping( "/blog" )
    public String blog( final Model model ) {
        List<Post> postsList;
        postsList = this.postService.findAll();
        model.addAttribute( "postList", postsList );
        return "blog";
    }

    @RequestMapping( "/post" )
    public String post( final Model model, @RequestParam( "id" ) final Long id ) {
        final Post post = this.postService.findById( id );
        model.addAttribute( "post", post );
        return "post";
    }

    @RequestMapping( "/searchUser" )
    public String searchBook( @ModelAttribute( "keyword" ) final String keyword, final HttpSession session,
            final Principal principal, final Model model,
            @PageableDefault( value = 28 ) final Pageable pageable ) {

        final Page<User> userList = this.userService.blurrySearch( keyword, pageable );
        final PageWrapper<User> page = new PageWrapper<User>( userList, "/directory" );
        model.addAttribute( "userList", page.getContent() );
        model.addAttribute( "page", page );

        if ( userList == null ) {
            model.addAttribute( "listEmpty", true );
            model.addAttribute( "directory", true );
            model.addAttribute( "noFilter", true );
            return "directory";
        }

        model.addAttribute( "userList", userList );
        model.addAttribute( "directory", true );
        model.addAttribute( "noFilter", true );
        return "directory";
    }

    @RequestMapping( value = "/filterResult", method = RequestMethod.POST )
    public String filterResultPost( final Model model, final HttpServletRequest request, final HttpSession session ) {

        String queryString = "SELECT distinct id from etincelles.user where user.enabled = true and user.first_name  is not null AND user.first_name != '' and ";
        final List<String> search = new ArrayList<>();
        boolean needAnd = false;
        boolean empty = true;

        if ( request.getParameterMap().containsKey( "skills" ) ) {
            final String[] skills = request.getParameterValues( "skills" );
            queryString = "SELECT distinct id from etincelles.user, etincelles.user_skill where user.enabled = true and user.first_name  is not null AND user.first_name != ''  and user.id = user_skill.user_id and";
            String skillIds = "";
            for ( int i = 0; i < skills.length; i++ ) {
                search.add( skills[i] );
                final Skill skill = this.skillRepo.findByname( skills[i] );
                skillIds += skill.getSkillId();
                if ( i != skills.length - 1 ) {
                    skillIds += ",";
                }
            }
            if ( needAnd == false ) {
                needAnd = true;
            }
            queryString += " user_skill.skill_id in (" + skillIds + ")";
        }

        if ( request.getParameterMap().containsKey( "sector" ) ) {
            final String[] sector = request.getParameterValues( "sector" );
            if ( needAnd ) {
                queryString += " and ";
            }
            String sectorString = "";
            for ( int i = 0; i < sector.length; i++ ) {
                search.add( sector[i] );
                sectorString += "'" + sector[i] + "'";
                if ( i != sector.length - 1 ) {
                    sectorString += ",";
                }
            }
            if ( needAnd == false ) {
                needAnd = true;
            }
            queryString += " user.sector in (" + sectorString + ")";
        }

        if ( request.getParameterMap().containsKey( "categories" ) ) {
            final String[] categories = request.getParameterValues( "categories" );
            if ( needAnd ) {
                queryString += " and ";
            }
            String categoryString = "";
            for ( int i = 0; i < categories.length; i++ ) {
                search.add( categories[i] );
                categoryString += "'" + categories[i] + "'";
                if ( i != categories.length - 1 ) {
                    categoryString += ",";
                }
            }
            if ( needAnd == false ) {
                needAnd = true;
            }
            queryString += " user.category in (" + categoryString + ")";
        }

        if ( request.getParameterMap().containsKey( "cities" ) ) {
            final String[] cities = request.getParameterValues( "cities" );
            if ( needAnd ) {
                queryString += " and ";
            }
            String cityString = "";
            for ( int i = 0; i < cities.length; i++ ) {
                search.add( cities[i] );
                cityString += "'" + cities[i] + "'";
                if ( i != cities.length - 1 ) {
                    cityString += ",";
                }
            }
            queryString += " user.city in (" + cityString + ")";
        }
        System.out.println( queryString );

        List<User> userList = null;
        userList = this.customUserService.searchQuery( queryString );
        try {
            userList.stream()
                    .sorted( ( object1, object2 ) -> object1.getLastName().compareTo( object2.getLastName() ) );
        } catch ( final Exception e ) {
            // TODO: handle exception
        }
        if ( !userList.isEmpty() ) {
            empty = false;
        }

        final List<String> skills = new ArrayList<>();
        final List<String> sectors = new ArrayList<>();
        final List<String> categoryList = new ArrayList<>();
        final List<String> cityList = new ArrayList<>();
        for ( final User user : userList ) {
            for ( final Skill skill : user.getSkills() ) {
                skills.add( skill.getName() );
            }
            if ( user.getSector() != null && user.getSector() != "" && !sectors.contains( user.getSector() ) ) {
                sectors.add( user.getSector() );
            }
            if ( user.getCategory() != null && !categoryList.contains( user.getCategory().toString() ) ) {
                categoryList.add( user.getCategory().toString() );
            }
            if ( user.getCity() != null && !cityList.contains( user.getCity().toString() ) ) {
                cityList.add( user.getCity().toString() );
            }
        }

        model.addAttribute( "skillList", skills );
        model.addAttribute( "sectors", sectors );
        model.addAttribute( "categoryList", categoryList );
        model.addAttribute( "cityList", cityList );
        model.addAttribute( "query", queryString );
        model.addAttribute( "listEmpty", empty );
        model.addAttribute( "userList", userList );
        model.addAttribute( "searchList", search );
        model.addAttribute( "directory", true );
        return "filterResult";
    }

    @RequestMapping( "/filterResult" )
    public String filterResult() {
        return "redirect:/directory";
    }

    @RequestMapping( value = "/contact", method = RequestMethod.POST )
    public String contact( final Model model, @RequestParam( "name" ) final String name,
            @RequestParam( "email" ) final String email, @RequestParam( "content" ) final String text,
            @RequestParam( "userEmail" ) final String userEmail, final HttpSession httpSession ) {
        final SimpleMailMessage newEmail = this.mailConstructor.constructContactEmail( name, email, text, userEmail );
        this.mailSender.send( newEmail );

        final User user = this.userService.findByEmail( userEmail );

        httpSession.setAttribute( "id", user.getId() );
        httpSession.setAttribute( "emailSent", true );
        return "redirect:/userDetail";
    }

    @RequestMapping( "/aboutUs" )
    public String aboutUs() {
        return "aboutUs";
    }

    @RequestMapping( value = "/deleteUser", method = RequestMethod.GET )
    public String deleteUser( Model model, @RequestParam( "id" ) Long id ) {
        userService.removeOne( id );
        model.addAttribute( "userRemoved", true );
        return "redirect:/logout";
    }

}
