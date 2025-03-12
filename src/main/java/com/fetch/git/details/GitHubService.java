package com.fetch.git.details;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GitHubService {
    private final RestTemplate restTemplate;
    @Value("${github.token}")
    private String githubToken;
    public GitHubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }
    public boolean isRateLimitExceeded() {
        String url = "https://api.github.com/rate_limit";
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        Map<String, Object> rateLimit = (Map<String, Object>) response.getBody().get("rate");
        int remaining = (int) rateLimit.get("remaining");
        return remaining <= 0;
    }
    @Cacheable("repos")
    public ResponseEntity<?> getRepositories(String username) {
        if (isRateLimitExceeded()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("GitHub API rate limit exceeded");
        }
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        String GITHUB_API = "https://api.github.com/users/" + username + "/repos";
        ResponseEntity<Map[]> repos = restTemplate.exchange(GITHUB_API, HttpMethod.GET, entity, Map[].class);
        List<Map<String, Object>> repoList = new ArrayList<>();
        if(repos.getBody()!=null) {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            for (Map<String,Object>repo : repos.getBody()) {
                LinkedHashMap<String, Object> repoData=new LinkedHashMap<>();
                repoData.put("Repo Name",repo.get("name"));
                repoData.put("URL",repo.get("html_url"));

                String langApi = (String) repo.get("languages_url");
                ResponseEntity<Map> langData =  restTemplate.exchange(langApi, HttpMethod.GET,entity,Map.class);
                if(langData.getBody()!=null){
                    Map <String,Double>langPercentage = calculateLanguagePercentage(langData.getBody());
                    repoData.put("languages", langPercentage);
                }

                String createdAt = (String) repo.get("created_at");
                String updatedAt = (String) repo.get("updated_at");
                if (createdAt != null) {
                    LocalDateTime createdDate = LocalDateTime.parse(createdAt, inputFormatter);
                    repoData.put("Created At", createdDate.format(outputFormatter));
                }
                if (updatedAt != null) {
                    LocalDateTime updatedDate = LocalDateTime.parse(updatedAt, inputFormatter);
                    repoData.put("Last modified", updatedDate.format(outputFormatter));
                }
                else if(createdAt!=null){
                    LocalDateTime createdDate = LocalDateTime.parse(createdAt, inputFormatter);
                    repoData.put("Last modified",createdDate.format(outputFormatter));
                }

                repoList.add(repoData);
            }
        }
        return new ResponseEntity<>(repoList, HttpStatus.OK);
    }
    @Cacheable("languages")
    public ResponseEntity<?> getLanguagePercentage(String username){
        if (isRateLimitExceeded()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("GitHub API rate limit exceeded");
        }
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        String GITHUB_API = "https://api.github.com/users/" + username + "/repos";
        ResponseEntity<Map[]> repos = restTemplate.exchange(GITHUB_API,HttpMethod.GET,entity,Map[].class);
        Map<String, Integer> langList = new HashMap<>();
        if(repos.getBody()!=null) {
            for(Map<String,Object>entry:repos.getBody()){
                String langApi = (String) entry.get("languages_url");
                ResponseEntity<Map> langResponse =  restTemplate.exchange(langApi, HttpMethod.GET,entity,Map.class);
                Map<String,Integer> langData=langResponse.getBody();
                if(langData!=null){
                    for(Map.Entry<String,Integer>entry1:langData.entrySet()){
                        langList.put(entry1.getKey(),langList.getOrDefault(entry1.getKey(),0)+entry1.getValue());
                    }
                }
            }
        }
        Map<String,Double> langPercentage=calculateLanguagePercentage(langList);
        return new ResponseEntity<>(langPercentage,HttpStatus.OK);
    }
    private static Map<String,Double> calculateLanguagePercentage(Map<String,Integer> langData){
        if (langData == null || langData.isEmpty()) {
            return Collections.singletonMap("No Data", 100.0);
        }
        int totalLines=langData.values().stream().mapToInt(Integer::intValue).sum();
        Map<String,Double> langPercentage=new HashMap<>();
        for(Map.Entry<String,Integer>entry:langData.entrySet()){
            langPercentage.put(entry.getKey(),(entry.getValue()*100.0)/totalLines);
        }
        return langPercentage;
    }
}
