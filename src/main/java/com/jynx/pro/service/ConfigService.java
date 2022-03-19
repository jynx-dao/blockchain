package com.jynx.pro.service;

import com.jynx.pro.entity.Config;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.ConfigRepository;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.utils.JSONUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConfigService {

    @Setter
    @Getter
    private Long timestamp;

    @Autowired
    private ConfigRepository configRepository;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private JSONUtils jsonUtils;

    /**
     * Initializes the database with the starting network configuration
     */
    public void initializeNetworkConfig(JSONObject appState) throws JSONException {
        Config config = jsonUtils.fromJson(
                appState.getJSONObject("networkConfig").toString(), Config.class);
        config.setId(1L);
        this.configRepository.save(config);
    }

    /**
     * Increment the seed used to generate deterministic UUIDs
     */
    public void incrementUuidSeed() {
        configRepository.save(get().setUuidSeed(get().getUuidSeed() + 1));
    }

    /**
     * Fetches the network config
     *
     * @return the {@link Config}
     */
    public Config get() {
        return configRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new JynxProException(ErrorCode.CONFIG_NOT_FOUND));
    }

    /**
     * Fetches the network config from {@link ReadOnlyRepository}
     *
     * @return the {@link Config}
     */
    public Config getStatic() {
        return readOnlyRepository.getAllByEntity(Config.class).stream().findFirst()
                .orElseThrow(() -> new JynxProException(ErrorCode.CONFIG_NOT_FOUND));
    }
}