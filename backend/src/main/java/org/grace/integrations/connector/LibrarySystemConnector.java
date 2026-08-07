package org.grace.integrations.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Core interface for all library and academic system connectors.
 * 
 * This interface defines the contract that all external system connectors must implement.
 * Connectors are responsible for communicating with external systems while keeping
 * external-system-specific logic isolated from GRACE core domain logic.
 * 
 * @param <T> The type of configuration object for this connector
 * @param <R> The type of raw record returned by the external system
 * @param <S> The type of search results returned by the external system
 */
public interface LibrarySystemConnector<T, R, S> {

    /**
     * Get the connector type identifier.
     * 
     * @return unique identifier for this connector type (e.g., "dspace", "koha", "vufind")
     */
    String getConnectorType();

    /**
     * Get the human-readable name of this connector.
     * 
     * @return display name for UI purposes
     */
    String getName();

    /**
     * Get the version of this connector implementation.
     * 
     * @return semantic version string
     */
    String getVersion();

    /**
     * Get the configuration schema for this connector.
     * 
     * The schema defines what configuration parameters are required/optional,
     * their types, validation rules, and descriptions for the admin UI.
     * 
     * @return JSON schema defining the configuration structure
     */
    Map<String, Object> getConfigSchema();

    /**
     * Initialize the connector with the given configuration.
     * 
     * @param config the configuration object
     * @throws ConnectorException if configuration is invalid or connection fails
     */
    void initialize(T config) throws ConnectorException;

    /**
     * Test the connection to the external system.
     * 
     * @return true if connection is successful
     * @throws ConnectorException if connection test fails
     */
    boolean testConnection() throws ConnectorException;

    /**
     * Check the health status of the connector.
     * 
     * @return health status including last success, last error, etc.
     */
    ConnectorHealth healthCheck();

    /**
     * Authenticate with the external system if required.
     * 
     * @return true if authentication successful or not required
     * @throws ConnectorException if authentication fails
     */
    boolean authenticate() throws ConnectorException;

    /**
     * Search the external system using the provided query.
     * 
     * @param query the search query
     * @param maxResults maximum number of results to return
     * @return search results wrapped in a connector result
     * @throws ConnectorException if search fails
     */
    ConnectorResult<S> search(String query, int maxResults) throws ConnectorException;

    /**
     * Get a single record by its identifier.
     * 
     * @param id the external system's record identifier
     * @return the record if found
     * @throws ConnectorException if retrieval fails
     */
    Optional<ConnectorRecord<R>> getRecord(String id) throws ConnectorException;

    /**
     * Get availability information for an item.
     * 
     * @param itemId the item identifier
     * @return availability status
     * @throws ConnectorException if retrieval fails
     */
    Optional<AvailabilityInfo> getAvailability(String itemId) throws ConnectorException;

    /**
     * Get holdings information for a bibliographic record.
     * 
     * @param bibId the bibliographic record identifier
     * @return holdings information
     * @throws ConnectorException if retrieval fails
     */
    Optional<HoldingsInfo> getHoldings(String bibId) throws ConnectorException;

    /**
     * Get a collection or set of records.
     * 
     * @param collectionId the collection identifier
     * @param offset pagination offset
     * @param limit pagination limit
     * @return collection records
     * @throws ConnectorException if retrieval fails
     */
    ConnectorResult<S> getCollection(String collectionId, int offset, int limit) throws ConnectorException;

    /**
     * Harvest records from the external system.
     * 
     * Used for batch synchronization operations.
     * 
     * @param from optional start date for incremental harvesting
     * @param until optional end date for incremental harvesting
     * @return harvest result containing records and resumption token if applicable
     * @throws ConnectorException if harvesting fails
     */
    HarvestResult<R> harvest(Instant from, Instant until) throws ConnectorException;

    /**
     * Synchronize data from the external system.
     * 
     * Higher-level operation that may combine harvesting, transformation, and loading.
     * 
     * @param syncOptions options controlling the synchronization behavior
     * @return synchronization result
     * @throws ConnectorException if synchronization fails
     */
    SyncResult sync(SyncOptions syncOptions) throws ConnectorException;

    /**
     * Close the connector and release any resources.
     */
    void close();

    /**
     * Get the capabilities supported by this connector.
     * 
     * @return set of supported capabilities
     */
    default ConnectorCapabilities getCapabilities() {
        return new ConnectorCapabilities();
    }
}
