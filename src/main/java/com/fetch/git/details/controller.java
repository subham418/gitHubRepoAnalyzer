package com.fetch.git.details;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/github")
public class controller {
    @Autowired
    private GitHubService gitHubService;

    @GetMapping("/{username}/repos")
    public ResponseEntity<?> getUserRepos(@PathVariable String username) {
        return gitHubService.getRepositories(username);
    }
    @GetMapping("{username}/lang/percentage")
    public ResponseEntity<?> getUserLangPercentage(@PathVariable String username){
        return gitHubService.getLanguagePercentage(username);
    }
    @GetMapping("/greet")
    public String greet(){
        return "Hello User";
    }
}