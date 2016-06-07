package yelp;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


import redis.clients.jedis.Jedis;

public class YelpAPI {

	private static final String CONSUMER_KEY = " Your consumer key";
	private static final String CONSUMER_SECRET = "Your consumer secret ";
	private static final String TOKEN = " Your token";
	private static final String TOKEN_SECRET = " your secret token";

	OAuthService service;
	Token accessToken;

	private static final String SEARCH_PATH = "/v2/search";
	private static final String BUSINESS_PATH = "/v2/business";
	private static final String API_HOST = "api.yelp.com";
	private static final String DEFAULT_TERM = "restaurants";
	private static final String DEFAULT_LOCATION = "514 W 37th Pl, Los Angeles, CA 90007";
	private static final String four_blocks = "500";
	private static final String one_mile = "1600";
	private static final String three_mile = "4800";
	private static final String LINE_SEPARATOR = "\r\n";
	// constructor
	public YelpAPI(String consumerKey, String consumerSecret, String token,
			String tokenSecret) {
		this.service = new ServiceBuilder().provider(YelpV2API.class)
				.apiKey(consumerKey).apiSecret(consumerSecret).build();
		this.accessToken = new Token(token, tokenSecret);
	}

	private OAuthRequest createOAuthRequest(String path) {
		OAuthRequest request = new OAuthRequest(Verb.GET, "http://" + API_HOST
				+ path);
		return request;
	}

	private String sendRequestAndGetResponse(OAuthRequest request) {
		System.out.println("Querying " + request.getCompleteUrl() + " ...");
		this.service.signRequest(this.accessToken, request);
		Response response = request.send();
		return response.getBody();
	}

	public String searchForBusinessesByLocation(String term, String location, String distance, int offset) {
		OAuthRequest request = createOAuthRequest(SEARCH_PATH);
		request.addQuerystringParameter("term", term);
		request.addQuerystringParameter("location", location);
		request.addQuerystringParameter("offset",String.valueOf(offset));
		return sendRequestAndGetResponse(request);
	}

	public String searchByBusinessId(String businessID) {
		OAuthRequest request = createOAuthRequest(BUSINESS_PATH + "/"
				+ businessID);
		return sendRequestAndGetResponse(request);
	}
	
	private static int jsonParser(YelpAPI yelpApi, String searchResponseJSON, int offset) throws IOException{
		
		JSONParser parser = new JSONParser();
		JSONObject response = null;
		try {
			response = (JSONObject) parser.parse(searchResponseJSON);
		} catch (ParseException pe) {
			System.out.println("Error: could not parse JSON response:");
			System.out.println(searchResponseJSON);
			System.exit(1);
		}
		/* 
		Get JSON results from Yelp. Deserialize and extract the useful information
		JSON return type ref: https://www.yelp.com/developers/documentation/v2/search_api
		*/
		JSONArray businesses = (JSONArray) response.get("businesses");
		int size = businesses.size();
		
		for (int i = 0; i < size; i++ ){
			JSONObject firstBusiness = (JSONObject) businesses.get(i);
			String firstBusinessID = firstBusiness.get("id").toString();
			String bizName = firstBusiness.get("name").toString();
			JSONArray cate = (JSONArray)firstBusiness.get("categories");
			String cateString = cate.get(0).toString();
			String rating = firstBusiness.get("rating").toString();
			String isClose = firstBusiness.get("is_closed").toString();
			String reviewCount = firstBusiness.get("review_count").toString();
			String url = null;
			if(firstBusiness.get("url")!=null){
				url = firstBusiness.get("url").toString();
			}
			JSONObject location = (JSONObject) firstBusiness.get("location");
			String address = location.get("display_address").toString();
			String phone = null;
			if(firstBusiness.get("phone")!=null){
				 phone = firstBusiness.get("phone").toString();
			}
			String city=location.get("city").toString();
			String post_code = null;
			if(firstBusiness.get("postal_code")!=null){
				post_code = location.get("postal_code").toString();
			}

			
			
			
			String oneRes = "The " + (i+1+offset)+" result title: "+ bizName+"\r\n"+"categories: "+ cateString +"\r\n"+"Rating: "+rating+"\r\n"+"URL: "+url+"\r\n"+"Address: "+address+"\r\n"+"Phone: "+phone+"\r\n"+"======================================";
			/* Serialize the useful information into JSON format and output */
			String jsonObj = "{\"title\": "+"\""+bizName+"\""+", "+"\"categories\": " + cateString + ", "+"\"rating\": " + rating + ", "+"\"url\": "+"\""+url+"\""+", "+"\"address\": "+ address+ ", "+"\"phone\": "+"\""+phone+"\""+ ", "+"\"is_closed\": "+"\""+isClose+"\""+ ", "+"\"review_count\": "+reviewCount+ ", "+"\"city\": "+"\""+city+"\""+ ", "+"\"postal_code\": "+"\""+post_code+"\""+"}";
			System.out.print(jsonObj);
			System.out.println();
			/* Using try and catch to write into file one item each time*/
			try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("myfileObj.json", true)))) {
			    out.println(jsonObj);
			}catch (IOException e) {
			    //exception handling
			    System.out.println("Error, can not find the file!");
			}
		}
		return size;
	}

	private static int queryAPI(YelpAPI yelpApi, String term, String postcode, String distance) throws IOException {
		
		int offset = 0;
		int count = 0;
		while (true){
			String searchResponseJSON = yelpApi.searchForBusinessesByLocation(term,
					postcode, distance, offset);
			System.out.println("===json===="+searchResponseJSON);
			int size = jsonParser(yelpApi, searchResponseJSON, offset);
			count += size;
			if (size < 20){
				return count;
			}
			offset += 20;
			if (offset == 1000){
				return 1000;
			}
		}
	}
	
	private static void query (String postcode, YelpAPI yelpApi, String term) throws IOException{
		
		System.out.println(term+" in nearby "+postcode);
		int count1 = queryAPI(yelpApi, term, postcode, four_blocks);
		System.out.println("four_blocks: "+count1);
		System.out.println();
	}

	public static void main(String[] args) throws IOException {

		YelpAPI yelpApi = new YelpAPI(CONSUMER_KEY, CONSUMER_SECRET, TOKEN,
				TOKEN_SECRET);
		String testPostcode = "90007";  //postcode used for YelpAPI to search
		String testTerm = "Restaurant"; //key word feature for YelpAPI to search
		query(testPostcode, yelpApi, testTerm);
		
		
	}
}
