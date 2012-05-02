package com.csc220.weather;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * @author Dharmdeo Singh
 * 
 */
public class WeatherBug implements LocationListener {
	// The API Key required by WB Service
	private String APIKey = "4x334tn2k4kdymuhjkezhpbh";
	private JSONObject json;

	// The base URL for hourly forecast with zip
	private String baseURL_hourly_zip = "http://i.wxbug.net/REST/Direct/GetForecastHourly.ashx?"
			+ "zip=ZZZZZ&ht=t&ht=d&ht=sc&ht=cp&ht=fl&ht=wd&ht=ws&ht=h&api_key=XXXXX";
	// The base URL for hourly forecast with lat and lon
	private String baseURL_hourly_loc = "http://i.wxbug.net/REST/Direct/GetForecastHourly.ashx?"
			+ "la=LAT&lo=LONG&ht=t&ht=d&ht=sc&ht=cp&ht=fl&ht=wd&ht=ws&ht=h&api_key=XXXXX";
	// The base URL for weekly forecast (location)
	private String baseURL_forecast_loc = "http://i.wxbug.net/REST/Direct/GetForecast.ashx?"
			+ "la=LAT&lo=LONG&nf=5&l=en&c=US&api_key=XXXXX";
	// The base URL for weekly forecast (zip)
	private String baseURL_forecast_zip = "http://i.wxbug.net/REST/Direct/GetForecast.ashx?"
			+ "zip=ZZZZZ&nf=5&l=en&c=US&api_key=XXXXX";
	// The base URL for advisory (zip)
	private String baseURL_advisory_zip = "http://i.wxbug.net/REST/Direct/GetAlert.ashx?"
			+ "zip=ZZZZZ&api_key=XXXXX";
	// The base URL for advisory (loc)
	private String baseURL_advisory_loc = "http://i.wxbug.net/REST/Direct/GetAlert.ashx?"
			+ "la=LAT&lo=LONG&api_key=XXXXX";

	private Handler UIHandler; // Used to handle updates from WeatherBug object
	private LocationManager locManager; // Used to get the users current
										// location
	private Geocoder geocoder; // This is needed to reverse geocode the location
								// to get a name
	private String city; // A string to store the info retreived from geocoder

	private String data = ""; // stores the data read from server
	private double lat = 0; // latitude of current network location
	private double log = 0; // longiture of current network location
	private boolean forecastUpdate = false;
	private boolean advisoryUpdate = false;

	ArrayList<DailyForecast> weeklyForecast; // stores the weekly forecast
	ArrayList<HourlyForecast> hourlyForecast; // stores the daily forecast
	ArrayList<WeatherAdvisory> weatherAdvisories; // stores the weather
													// advisories

	private String urlString; // used when requesting data from WeatherBug

	// Constants to indicate what has been updated
	public static final int CURRENT = 1;
	public static final int FORECAST = 2;
	public static final int ADVISORY = 3;

	/**
	 * @param handler
	 *            A Handler which will be notified when a weather update has
	 *            occurred. The message sent will contain the type of update
	 *            that has occurred (current weather, forecast, etc.)
	 */
	public WeatherBug(Handler handler, Context context) {
		UIHandler = handler;

		// The location manager for getting the coarse location of the user
		locManager = (LocationManager) context
				.getSystemService(Context.LOCATION_SERVICE);

		// A geocoder object to get a human readable name for the location

		geocoder = new Geocoder(context);
	}

	/**
	 * Update the current weather. When results are available, a message will be
	 * dispatched to the handler notifying it that the current weather has been
	 * updated via the message's arg1 variable. The variable will be set to
	 * WeatherBug.CURRENT.
	 * 
	 * This is a generic update function. The string storing the URL is changed
	 * using the other update functions
	 */
	private void updateCurent() {
		/*
		 * Create a new thread to run in the background. This is to ensure the
		 * UI does not freeze up while retrieving data from the web server.
		 * Without this background thread, the UI would stall until the data is
		 * downloaded.
		 */
		Thread background = new Thread() {
			@Override
			public void run() {

				Log.i("WeatherBug", urlString);
				// Download the JSON data
				try {
					// Open a new URL Connection and download the data
					URL url = new URL(urlString);
					URLConnection weatherBugConnection = url.openConnection();
					BufferedReader input = new BufferedReader(
							new InputStreamReader(
									weatherBugConnection.getInputStream()));
					String inputLine;
					// Keep reading lines until there is no more to be read
					while ((inputLine = input.readLine()) != null) {
						data += inputLine;
					}
					Log.i("WeatherBug", data);

					// Create a JSON object from the data that was read
					json = new JSONObject(data);
					data = "";
					input.close(); // Close the input stream
					/*
					 * Obtain a list of the hourly forecast data. This is a list
					 * of JSON objects which each contain information about a
					 * certain hour's weather data. The first JSON object is the
					 * current weather status.
					 */
					JSONArray hourlyForecastList = json
							.getJSONArray("forecastHourlyList");

					/*
					 * // Pick the first JSON object from the array JSONObject
					 * hour1 = hourlyForecast.getJSONObject(0);
					 * 
					 * // Obtain the temperature and description of the current
					 * // weather temp = hour1.getInt("temperature"); desc =
					 * hour1.getString("desc");
					 */
					hourlyForecast = new ArrayList<HourlyForecast>();
					JSONObject hour;
					HourlyForecast forecast;
					int j = 0;
					for (int i = 0; i < hourlyForecastList.length()
							&& i < 8 + j; i++) {
						hour = hourlyForecastList.getJSONObject(i);
						forecast = new HourlyForecast(hour.getLong("dateTime"),
								hour.getInt("temperature"),
								hour.getString("desc"),
								hour.getString("skyCover"),
								hour.getString("chancePrecip"),
								hour.getString("feelsLike"),
								hour.getString("feelsLikeLabel"),
								hour.getString("windDir"),
								hour.getString("windSpeed"),
								hour.getString("dewPoint"),
								hour.getString("humidity"));
						Log.i("WeatherBug", forecast.getTemp());
						if (!forecast.outOfScope()) {
							hourlyForecast.add(forecast);
						} else {
							j++;
						}
						// only obtain the image for the current weather
						if(hourlyForecast.size() == 1)
							hourlyForecast.get(0).setImage(hour.getString("icon"));
					}

					// A new runnable to post to the UI thread
					Runnable update = new Runnable() {

						@Override
						public void run() {
							/*
							 * Create and send a message to the handler running
							 * on the UI thread indicating that the current
							 * weather status has been updated.
							 */
							Message msg = new Message();
							msg.arg1 = CURRENT;
							UIHandler.dispatchMessage(msg);
						}
					};
					/*
					 * Post the runnable to the handler. This allows the handler
					 * to continue to run on the UI thread so the views can be
					 * updated
					 */
					UIHandler.post(update);

				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};
		background.start();
	}

	/**
	 * This method updates the 5 day forecast after the URL has been tampered by
	 * the other forecast update methods (one for zip and one for location).
	 * This method will notify the handler when all data has been downloaded and
	 * parsed. Message dispatched to the handler will have it's arg1 set to
	 * WeatherBug.FORECAST to indicate the forecast was updated.
	 */
	private void updateForecast() {
		/*
		 * Create a new thread to run in the background. This is to ensure the
		 * UI does not freeze up while retrieving data from the web server.
		 * Without this background thread, the UI would stall until the data is
		 * downloaded.
		 */
		Thread background = new Thread() {
			@Override
			public void run() {

				Log.i("WeatherBug", urlString);
				// Download the JSON data
				try {
					// Open a new URL Connection and download the data
					URL url = new URL(urlString);
					URLConnection weatherBugConnection = url.openConnection();
					BufferedReader input = new BufferedReader(
							new InputStreamReader(
									weatherBugConnection.getInputStream()));
					String inputLine;
					// Keep reading lines until there is no more to be read
					while ((inputLine = input.readLine()) != null) {
						data += inputLine;
					}
					Log.i("WeatherBug", data);

					// Create a JSON object from the data that was read
					json = new JSONObject(data);
					data = "";
					input.close(); // Close the input stream
					/*
					 * Obtain a list of the forecast data. This is a list of
					 * JSON objects which each contain information about a
					 * certain day's weather data. The first JSON object is the
					 * today's weather status
					 */
					JSONArray forecast = json.getJSONArray("forecastList");

					// An array list to store every day of the 5 day forecast
					weeklyForecast = new ArrayList<DailyForecast>();
					// Each day is a JSON object
					JSONObject dayForecast = null;
					String high, low, title, desc, pred, name;
					DailyForecast day; // each day is a daily forecast object
					for (int i = 0; i < 5; i++) {
						/*
						 * Get each of the 5 days requested from the JSONArray
						 * and retrieve their high, low, and title. These are
						 * the required fields for the DailyForecast
						 * constructor. Create a DailyForecast object for each
						 * day. Retrieve all the other information about each
						 * day as well.
						 */
						dayForecast = forecast.getJSONObject(i);
						high = dayForecast.getString("high");
						low = dayForecast.getString("low");
						title = dayForecast.getString("title");
						day = new DailyForecast(title, high, low,
								dayForecast.getString("dayIcon"),
								dayForecast.getString("nightIcon"));

						// Retrieve and add the day details for each day
						desc = dayForecast.getString("dayDesc");
						pred = dayForecast.getString("dayPred");
						name = dayForecast.getString("dayTitle");
						day.setDayDetails(desc, pred, name);

						// Retrieve and add the night details for each day
						desc = dayForecast.getString("nightDesc");
						pred = dayForecast.getString("nightPred");
						name = dayForecast.getString("nightTitle");

						// Add the day to the weekly forecast
						weeklyForecast.add(day);
						Log.i("WeatherBug", day.toString());
					}

					// A new runnable to post to the UI thread
					Runnable update = new Runnable() {

						@Override
						public void run() {
							/*
							 * Create and send a message to the handler running
							 * on the UI thread indicating that the current
							 * weather status has been updated.
							 */
							Message msg = new Message();
							msg.arg1 = FORECAST;
							UIHandler.dispatchMessage(msg);
						}
					};
					/*
					 * Post the runnable to the handler. This allows the handler
					 * to continue to run on the UI thread so the views can be
					 * updated
					 */
					UIHandler.post(update);

				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};
		background.start();
	}

	/**
	 * This allows you to get the weather (hourly) at the specified zip code for
	 * the current day
	 * 
	 * @param zip
	 *            The zip code to get weather information about
	 */
	public void updateCurrentWithZip(String zip) {
		/*
		 * Modifying the base url for hourly updates to include the correct zip
		 * code and API key.
		 */
		getCityFromZip(zip);
		urlString = baseURL_hourly_zip.replace("ZZZZZ", zip);
		urlString = urlString.replace("XXXXX", APIKey);
		// Call the generic update function to get the current weather
		updateCurent();
	}

	/**
	 * Update the current weather using the device's location (via Network
	 * location)
	 */
	public void updateCurrentWithLoc() {

		// Start listening for location updates
		locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0,
				0, this);

		/*
		 * A new thread for running in the background. This waits for the
		 * updated location therefore this thread will wait a while.
		 */
		Thread wait = new Thread() {
			@Override
			public void run() {
				/*
				 * Synchronized because this function will wait for updates.
				 */
				synchronized (locManager) {

					try {
						// wait until the the LocationManager updates the
						// location
						locManager.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Log.i("WeatherBug", "Lat: " + lat + " Long: " + log);

					/*
					 * Modify the base url for hourly updates to include the
					 * correct latitude and longitude
					 */
					if (forecastUpdate) {
						urlString = baseURL_forecast_loc.replace("LAT",
								Double.toString(lat));
					} else if (advisoryUpdate) {
						urlString = baseURL_advisory_loc.replace("LAT",
								Double.toString(lat));
					} else {
						urlString = baseURL_hourly_loc.replace("LAT",
								Double.toString(lat));
					}
					urlString = urlString.replace("LONG", Double.toString(log));
					urlString = urlString.replace("XXXXX", APIKey);
					// Call the generic update function to get the current
					// weather
					if (forecastUpdate) {
						updateForecast();
						forecastUpdate = false;
					} else if (advisoryUpdate) {
						updateAdvisory();
						advisoryUpdate = false;
					} else {
						updateCurent();
					}
					super.run();
				}
			}
		};
		wait.start();
	}

	/**
	 * Get the 5 day forecast of the user's current location (Network Location).
	 * The handler will be notified that the forecast is updated (the message's
	 * arg1 will be set to WeatherBug.FORECAST)
	 */
	public void updateForecastWithLoc() {
		// This boolean is used to call the updateForecast method later from the
		// updateCurrentWithLoc method.
		forecastUpdate = true;
		// Simplifies getting the location
		updateCurrentWithLoc();
	}

	/**
	 * Update the 5 day forecast with a zip code
	 * 
	 * @param zip
	 *            The zip to use for the 5 day forecast
	 */
	public void updateForecastWithZip(String zip) {
		getCityFromZip(zip);
		// Place the zip and API key into the corresponding base URL
		urlString = baseURL_forecast_zip.replace("ZZZZZ", zip);
		urlString = urlString.replace("XXXXX", APIKey);
		// Call the generic update function to get the current weather
		updateForecast();
	}

	/**
	 * Retrieve as list of the daily forecasts
	 * 
	 * @return A list of the forecast for each of the next 5 days
	 */
	public ArrayList<DailyForecast> get5DayForecast() {
		return weeklyForecast;
	}

	/**
	 * @return A list of the forecast for the next 8 hours
	 */
	public ArrayList<HourlyForecast> getHourlyForecast() {
		return hourlyForecast;
	}

	/**
	 * @return The city for the current weather forecast
	 */
	public String getCity() {
		return city;
	}

	/**
	 * Set the city using the location information
	 * 
	 * @param address
	 *            The address to use
	 */
	private void setCity(Address address) {
		city = address.getSubLocality();

		// remove any null subLocality
		if (city == null)
			city = "";
		else
			city += ", ";
		city += address.getAdminArea();
		Log.i("WeatherBug", city);
	}

	/**
	 * Gets the corresponding address info from a zip
	 * 
	 * @param zip
	 *            The zip to use
	 */
	private void getCityFromZip(String zip) {
		Log.i("WeatherBug", zip);
		try {
			List<Address> addresses = geocoder.getFromLocationName(zip, 1);
			if (addresses.size() > 0)
				setCity(addresses.get(0));
			else
				city = "null";

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Puts together the proper URL to get advisories from a zip code and passes
	 * it to the method which will get the advisory information
	 * 
	 * @param zip
	 *            The zip code to get advisories for
	 */
	public void updateAdvisoryWithZip(String zip) {
		// Retrieve the city information from the zip
		getCityFromZip(zip);
		urlString = baseURL_advisory_zip.replace("ZZZZZ", zip);
		urlString = urlString.replace("XXXXX", APIKey);
		updateAdvisory();
	}

	/**
	 * Uses the current location of the user to find any weather advisories in
	 * that location
	 */
	public void updateAdvisoryWithLoc() {
		advisoryUpdate = true; // used in updateCurrentWithLoc
		// Recycling the updateCurrentWithLoc
		updateCurrentWithLoc();
	}

	/**
	 * The generic updateAdvisory function. Used to get the avisories based on
	 * the URL set by the updateAdvisoryWithLoc or updateAdvisoryWithZip methods
	 */
	private void updateAdvisory() {
		/*
		 * Create a new thread to run in the background. This is to ensure the
		 * UI does not freeze up while retrieving data from the web server.
		 * Without this background thread, the UI would stall until the data is
		 * downloaded.
		 */
		Thread background = new Thread() {
			@Override
			public void run() {

				Log.i("WeatherBug", urlString);
				// Download the JSON data
				try {
					// Open a new URL Connection and download the data
					URL url = new URL(urlString);
					URLConnection weatherBugConnection = url.openConnection();
					BufferedReader input = new BufferedReader(
							new InputStreamReader(
									weatherBugConnection.getInputStream()));
					String inputLine;
					// Keep reading lines until there is no more to be read
					while ((inputLine = input.readLine()) != null) {
						data += inputLine;
					}
					Log.i("WeatherBug", data);

					// Create a JSON object from the data that was read
					json = new JSONObject(data);
					data = "";
					input.close(); // Close the input stream
					/*
					 * Obtain a list of the hourly forecast data. This is a list
					 * of JSON objects which each contain information about a
					 * certain hour's weather data. The first JSON object is the
					 * current weather status.
					 */
					int count = json.getInt("alertCount");
					Log.i("WeatherBug", "Alerts: " + count);
					JSONArray advisories = json.getJSONArray("alertList");
					JSONObject advisory;
					WeatherAdvisory wa;
					weatherAdvisories = new ArrayList<WeatherAdvisory>();
					if (count > 0) {
						// Only get alerts of the count is more than 0
						for (int i = 0; i < count; i++) {
							advisory = advisories.getJSONObject(i);
							wa = new WeatherAdvisory(
									advisory.getString("dateTimeBegins"),
									advisory.getString("dateTimeEnds"));
							wa.setDetails(advisory.getString("description"),
									advisory.getString("message"));
							weatherAdvisories.add(wa);
						}
					}

					// A new runnable to post to the UI thread
					Runnable update = new Runnable() {

						@Override
						public void run() {
							/*
							 * Create and send a message to the handler running
							 * on the UI thread indicating that the current
							 * weather status has been updated.
							 */
							Message msg = new Message();
							msg.arg1 = ADVISORY;
							UIHandler.dispatchMessage(msg);
						}
					};
					/*
					 * Post the runnable to the handler. This allows the handler
					 * to continue to run on the UI thread so the views can be
					 * updated
					 */
					UIHandler.post(update);

				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};
		background.start();
	}

	/**
	 * @return A list of the weather advisories
	 */
	public ArrayList<WeatherAdvisory> getAdvisories() {
		return weatherAdvisories;
	}

	// ===============LOCATION LISTENER METHODS===========================

	@Override
	public void onLocationChanged(Location location) {
		synchronized (locManager) {
			// Save the latitude and longitude of the current location
			lat = location.getLatitude();
			log = location.getLongitude();
			// Stop listening for updates (only need one location)
			locManager.removeUpdates(this);

			// Reverse geocoding

			try {
				Address address = (Address) geocoder.getFromLocation(lat, log,
						1).get(0);
				setCity(address);
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Notify the location manager which is waiting for the location
			locManager.notify();

		}
	}

	@Override
	public void onProviderDisabled(String provider) {
		// unimplemented
	}

	@Override
	public void onProviderEnabled(String provider) {
		// unimplemented
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// unimplemented
	}
}
