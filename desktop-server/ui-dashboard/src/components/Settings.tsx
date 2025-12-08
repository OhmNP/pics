import { useState, useEffect } from 'react';
import { Box, Typography, Card, CardContent, TextField, Button, Grid, Divider, Alert } from '@mui/material';
import { api, ServerConfig } from '../services/api';
import SaveIcon from '@mui/icons-material/Save';
import { ThumbnailRegenerator } from './ThumbnailRegenerator';

export default function Settings() {
    const [config, setConfig] = useState<ServerConfig | null>(null);
    const [loading, setLoading] = useState(true);
    const [saved, setSaved] = useState(false);

    useEffect(() => {
        const fetchConfig = async () => {
            try {
                const response = await api.getConfig();
                setConfig(response.data);
            } catch (err) {
                console.error('Error fetching config:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchConfig();
    }, []);

    const handleSave = async () => {
        if (!config) return;

        try {
            await api.updateConfig(config);
            setSaved(true);
            setTimeout(() => setSaved(false), 3000);
        } catch (err) {
            console.error('Error saving config:', err);
        }
    };

    if (loading || !config) {
        return <Box p={3}><Typography>Loading...</Typography></Box>;
    }

    return (
        <Box p={3}>
            <Typography variant="h4" fontWeight="bold" gutterBottom>
                Server Settings
            </Typography>

            {saved && (
                <Alert severity="success" sx={{ mb: 3 }}>
                    Configuration saved! Restart the server to apply changes.
                </Alert>
            )}

            <Grid container spacing={3}>
                {/* Network Settings */}
                <Grid item xs={12} md={6}>
                    <Card>
                        <CardContent>
                            <Typography variant="h6" gutterBottom>
                                Network
                            </Typography>
                            <Divider sx={{ mb: 2 }} />

                            <TextField
                                fullWidth
                                label="Sync Port"
                                type="number"
                                value={config.network.port}
                                onChange={(e) => setConfig({
                                    ...config,
                                    network: { ...config.network, port: parseInt(e.target.value) }
                                })}
                                margin="normal"
                            />

                            <TextField
                                fullWidth
                                label="Max Connections"
                                type="number"
                                value={config.network.maxConnections}
                                onChange={(e) => setConfig({
                                    ...config,
                                    network: { ...config.network, maxConnections: parseInt(e.target.value) }
                                })}
                                margin="normal"
                            />

                            <TextField
                                fullWidth
                                label="Timeout (seconds)"
                                type="number"
                                value={config.network.timeout}
                                onChange={(e) => setConfig({
                                    ...config,
                                    network: { ...config.network, timeout: parseInt(e.target.value) }
                                })}
                                margin="normal"
                            />
                        </CardContent>
                    </Card>
                </Grid>

                {/* Storage Settings */}
                <Grid item xs={12} md={6}>
                    <Card>
                        <CardContent>
                            <Typography variant="h6" gutterBottom>
                                Storage
                            </Typography>
                            <Divider sx={{ mb: 2 }} />

                            <TextField
                                fullWidth
                                label="Photos Directory"
                                value={config.storage.photosDir}
                                onChange={(e) => setConfig({
                                    ...config,
                                    storage: { ...config.storage, photosDir: e.target.value }
                                })}
                                margin="normal"
                            />

                            <TextField
                                fullWidth
                                label="Database Path"
                                value={config.storage.dbPath}
                                onChange={(e) => setConfig({
                                    ...config,
                                    storage: { ...config.storage, dbPath: e.target.value }
                                })}
                                margin="normal"
                            />

                            <TextField
                                fullWidth
                                label="Max Storage (GB)"
                                type="number"
                                value={config.storage.maxStorageGB}
                                onChange={(e) => setConfig({
                                    ...config,
                                    storage: { ...config.storage, maxStorageGB: parseInt(e.target.value) }
                                })}
                                margin="normal"
                            />
                        </CardContent>
                    </Card>
                </Grid>

                {/* Logging Settings */}
                <Grid item xs={12}>
                    <Card>
                        <CardContent>
                            <Typography variant="h6" gutterBottom>
                                Logging
                            </Typography>
                            <Divider sx={{ mb: 2 }} />

                            <Grid container spacing={2}>
                                <Grid item xs={12} md={4}>
                                    <TextField
                                        fullWidth
                                        label="Log Level"
                                        value={config.logging.level}
                                        onChange={(e) => setConfig({
                                            ...config,
                                            logging: { ...config.logging, level: e.target.value }
                                        })}
                                        select
                                        SelectProps={{ native: true }}
                                    >
                                        <option value="DEBUG">DEBUG</option>
                                        <option value="INFO">INFO</option>
                                        <option value="WARN">WARN</option>
                                        <option value="ERROR">ERROR</option>
                                    </TextField>
                                </Grid>

                                <Grid item xs={12} md={4}>
                                    <TextField
                                        fullWidth
                                        label="Log File"
                                        value={config.logging.file}
                                        onChange={(e) => setConfig({
                                            ...config,
                                            logging: { ...config.logging, file: e.target.value }
                                        })}
                                    />
                                </Grid>

                                <Grid item xs={12} md={4}>
                                    <TextField
                                        fullWidth
                                        label="Console Output"
                                        value={config.logging.consoleOutput ? 'Yes' : 'No'}
                                        onChange={(e) => setConfig({
                                            ...config,
                                            logging: { ...config.logging, consoleOutput: e.target.value === 'Yes' }
                                        })}
                                        select
                                        SelectProps={{ native: true }}
                                    >
                                        <option value="Yes">Yes</option>
                                        <option value="No">No</option>
                                    </TextField>
                                </Grid>
                            </Grid>
                        </CardContent>
                    </Card>
                </Grid>

                {/* Maintenance Tools */}
                <Grid item xs={12}>
                    <ThumbnailRegenerator />
                </Grid>
            </Grid>

            {/* Save Button */}
            <Box mt={3} display="flex" justifyContent="flex-end">
                <Button
                    variant="contained"
                    startIcon={<SaveIcon />}
                    onClick={handleSave}
                    size="large"
                >
                    Save Configuration
                </Button>
            </Box>
        </Box>
    );
}
