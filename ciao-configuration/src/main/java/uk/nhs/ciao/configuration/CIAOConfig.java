/*
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package uk.nhs.ciao.configuration;

import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.nhs.ciao.configuration.impl.EtcdPropertyStore;
import uk.nhs.ciao.configuration.impl.FilePropertyStore;
import uk.nhs.ciao.configuration.impl.CipProperties;
import uk.nhs.ciao.exceptions.CIAOConfigurationException;

/**
 * The first time a CIP is run, it will attempt to connect to the etcd URL provided.
 * If it is able to connect, it will check whether configuration already exists for
 * this CIP. It will do this by looking for the `/ciao/cipname/version` key. If
 * config already exists, it will be used immediately. If no config exists, a
 * default set of configuration values will be added to the etcd repository.
 * If the CIP is unable to connect to the provided URL, an error will be returned.
 * 
 * If no etcd URL is provided, the CIP will look for a local configuration file
 * with the name `cipname-version.properties`. It will look in the path specified
 * or in a .ciao directory in the user's home directory if no path is given.
 * If the file is found, it will be used, and if not, a default
 * configuration file will be created.
 * 
 * All configuration values in CIAO CIPs should be accessed using this class.
 * 
 * @author Adam Hatherly
 */
public class CIAOConfig {	
	private CipProperties cipProperties = null;
	private static Logger logger = LoggerFactory.getLogger(CIAOConfig.class);

	/**
	 * Constructor to initialise the configuration for a CIAO CIP.
	 * @param args Command line arguments passed to the CIP, these will be used
	 * to identify the URL for etcd and/or the Path to look for a config file
	 * @param cipName Name of CIP
	 * @param version Version number of CIP
	 * @param defaultConfig Java properties object with default config values for CIP
	 * @throws Exception 
	 */
	public CIAOConfig(String args[], String cipName, String version, Properties defaultConfig) throws CIAOConfigurationException {
		CommandLineParser.initialiseFromCLIArguments(this, args, cipName, version, defaultConfig);
	}
	
	/**
	 * Constructor to initialise the configuration for a CIAO CIP.
	 * @param etcdURL URL for etcd (or null to use a local config file)
	 * @param configFilePath Path to look for config file
	 * @param cipName Name of CIP
	 * @param version Version number of CIP
	 * @param defaultConfig Java properties object with default config values for CIP
	 * @param classifier An optional classifier to allow multiple versions of config to exist for different running CIPs
	 * @throws CIAOConfigurationException
	 */
	public CIAOConfig(String etcdURL, String configFilePath,
			String cipName, String version, Properties defaultConfig, String classifier) throws CIAOConfigurationException {
		
		initialise(etcdURL, configFilePath, cipName, version, defaultConfig, classifier);
	}
	
	/**
	 * Constructor to initialise the configuration for a CIAO CIP.
	 * @param etcdURL URL for etcd (or null to use a local config file)
	 * @param configFilePath Path to look for config file
	 * @param cipName Name of CIP
	 * @param version Version number of CIP
	 * @param defaultConfig Java properties object with default config values for CIP
	 * @throws CIAOConfigurationException 
	 */
	public CIAOConfig(String etcdURL, String configFilePath,
			String cipName, String version, Properties defaultConfig) throws CIAOConfigurationException {
		initialise(etcdURL, configFilePath, cipName, version, defaultConfig, null);
	}
	
	/**
	 * Constructs a CIAOConfig instance backed by the specified property store
	 * 
	 * @param propertyStore The store which holds this configurations properties
	 */
	public CIAOConfig(final CipProperties cipProperties) {
		if (cipProperties == null) {
			throw new NullPointerException("cipProperties");
		}
		
		this.cipProperties = cipProperties;
	}
	
	/**
	 * The name of the CIP
	 * 
	 * @return The name of the CIP
	 * @throws CIAOConfigurationException If the configuration has not been initialised correctly
	 */
	public String getCipName() throws CIAOConfigurationException {
		requireCipProperties();
		return this.cipProperties.getCipName();
	}
	
	/**
	 * The version of the CIP
	 * 
	 * @return The version of the CIP
	 * @throws CIAOConfigurationException If the configuration has not been initialised correctly
	 */
	public String getVersion() throws CIAOConfigurationException {
		requireCipProperties();
		return this.cipProperties.getVersion();
	}
	
	/**
	 * Method to retrieve the configuration value for a given key
	 * @param key
	 * @return value of configuration item
	 * @throws Exception if the configuration path was not initialised correctly
	 */
	public String getConfigValue(String key) throws CIAOConfigurationException {
		requireCipProperties();
		return this.cipProperties.getConfigValue(key);
	}
	
	/**
	 * Returns the set of keys associated with this configuration
	 * @return A set of configuration keys
	 * @throws CIAOConfigurationException if the configuration path was not initialised correctly
	 */
	public Set<String> getConfigKeys() throws CIAOConfigurationException {
		requireCipProperties();
		return this.cipProperties.getConfigKeys();
	}
	
	/**
	 * Returns a java properties object containing all configuration values
	 * @return Java properties object
	 * @throws Exception If unable to retrieve config values
	 */
	public Properties getAllProperties() throws CIAOConfigurationException {
		requireCipProperties();
		return this.cipProperties.getAllProperties();
	}
	
	@Override
	public String toString() {
		if (this.cipProperties == null) {
			return "Config not initialised";
		} else {
			return this.cipProperties.toString();
		}
	}
	
	protected void initialise(String etcdURL, String configFilePath,
			String cipName, String version, Properties defaultConfig, String classifier) throws CIAOConfigurationException {
		
		// See if we have an ETCD URL
		if (etcdURL != null) {
			EtcdPropertyStore etcd = new EtcdPropertyStore(etcdURL);
			try {
				if (etcd.versionExists(cipName, version, classifier)) {
					logger.info("Found etcd config at URL: " + etcdURL);
					this.cipProperties = etcd.loadConfig(cipName, version, classifier);
				} else {
					logger.debug("etcd config not yet initialised for this CIP");
					if (defaultConfig == null) {
						throw new CIAOConfigurationException("No default CIP config was provided - unable to initialise CIP");
					}
					this.cipProperties = etcd.setDefaults(cipName, version, classifier, defaultConfig);
					logger.info("Initialised default etcd config for this CIP at URL: " + etcdURL);
				}
			} catch (Exception e) {
				logger.error("Can't connect to ETCD URL provided");
				throw new CIAOConfigurationException(e);
			}
		} else {
			logger.debug("No ETCD URL provided, using local configuration");
			// Fall-back on file-based config
			FilePropertyStore fileStore = new FilePropertyStore(configFilePath);
			if (fileStore.versionExists(cipName, version, classifier)) {
				logger.info("Found file-based config at path: {}", fileStore.getPath());
				this.cipProperties = fileStore.loadConfig(cipName, version, classifier);
			} else {
				this.cipProperties = fileStore.setDefaults(cipName, version, classifier, defaultConfig);
				logger.info("Initialised default file-based config for this CIP at path: {}", fileStore.getPath());
			}
		}
	}
	
	/**
	 * Checks that the backing CipProperties is in a valid state
	 * @throws CIAOConfigurationException If the CipProperties is not valid
	 */
	private void requireCipProperties() throws CIAOConfigurationException {
		if (this.cipProperties == null) {
			throw new CIAOConfigurationException("Configuration not initialised correctly - see error logs for details.");
		}
	}
}
