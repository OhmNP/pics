import { useEffect, useState } from 'react';
import { Box, Typography, Paper, Grid, Chip, Button, List, ListItem, ListItemText, Divider, Tab, Tabs, IconButton } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import DownloadIcon from '@mui/icons-material/Download';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import QuestionMarkIcon from '@mui/icons-material/QuestionMark';
import { api, IntegrityReport, IntegrityDetailsResponse } from '../services/api';

export default function Integrity() {
    const [status, setStatus] = useState<IntegrityReport | null>(null);
    const [loading, setLoading] = useState(false);
    const [tabValue, setTabValue] = useState(0);
    const [details, setDetails] = useState<string[]>([]);
    const [detailsType, setDetailsType] = useState<string>('missing'); // missing, corrupt, orphan
    const [loadingDetails, setLoadingDetails] = useState(false);

    const fetchStatus = async () => {
        setLoading(true);
        try {
            const res = await api.getIntegrityStatus();
            setStatus(res.data);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const fetchDetails = async (type: string) => {
        setLoadingDetails(true);
        try {
            const res = await api.getIntegrityDetails(type, 100); // Limit 100 for display
            setDetails(res.data.items);
            setDetailsType(type);
        } catch (err) {
            console.error(err);
        } finally {
            setLoadingDetails(false);
        }
    };

    useEffect(() => {
        fetchStatus();
    }, []);

    useEffect(() => {
        // Fetch details when tab changes
        if (tabValue === 0) fetchDetails('missing'); // Default mapping? 
        // Actually let's map tabs to types
        const types = ['missing', 'corrupt', 'orphan'];
        if (types[tabValue]) {
            fetchDetails(types[tabValue]);
        }
    }, [tabValue]);

    const handleExport = () => {
        if (!status) return;
        const dataStr = JSON.stringify(status, null, 2);
        const dataUri = 'data:application/json;charset=utf-8,' + encodeURIComponent(dataStr);

        const exportFileDefaultName = 'integrity_report.json';

        const linkElement = document.createElement('a');
        linkElement.setAttribute('href', dataUri);
        linkElement.setAttribute('download', exportFileDefaultName);
        linkElement.click();
    };

    const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
        setTabValue(newValue);
    };

    return (
        <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <div>
                    <Typography variant="h4" fontWeight="800" className="gradient-text" gutterBottom>
                        Data Integrity
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        Scan and verify photo library consistency
                    </Typography>
                </div>
                <Box display="flex" gap={2}>
                    <Button
                        startIcon={<DownloadIcon />}
                        variant="outlined"
                        onClick={handleExport}
                        disabled={!status}
                    >
                        Export Report
                    </Button>
                    <IconButton onClick={fetchStatus} disabled={loading} sx={{ color: 'var(--primary)' }}>
                        <RefreshIcon />
                    </IconButton>
                </Box>
            </Box>

            {status && (
                <Grid container spacing={3} mb={4}>
                    <Grid item xs={12} md={3}>
                        <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, textAlign: 'center' }}>
                            <Typography color="text.secondary" mb={1}>Overall Status</Typography>
                            <Chip
                                label={status.status}
                                color={status.status === 'idle' ? 'success' : 'warning'}
                                icon={status.status === 'idle' ? <CheckCircleIcon /> : <RefreshIcon />}
                            />
                            <Typography variant="caption" display="block" mt={2} color="text.secondary">
                                Last Scan: {new Date(status.lastScan).toLocaleString()}
                            </Typography>
                        </Paper>
                    </Grid>
                    <Grid item xs={12} md={3}>
                        <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, borderLeft: '4px solid #ef4444' }}>
                            <Typography color="text.secondary">Missing Blobs</Typography>
                            <Typography variant="h3" fontWeight="bold" color={status.missingBlobs > 0 ? "error" : "text.primary"}>
                                {status.missingBlobs}
                            </Typography>
                        </Paper>
                    </Grid>
                    <Grid item xs={12} md={3}>
                        <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, borderLeft: '4px solid #f59e0b' }}>
                            <Typography color="text.secondary">Corrupt Files</Typography>
                            <Typography variant="h3" fontWeight="bold" color={status.corruptBlobs > 0 ? "warning.main" : "text.primary"}>
                                {status.corruptBlobs}
                            </Typography>
                        </Paper>
                    </Grid>
                    <Grid item xs={12} md={3}>
                        <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, borderLeft: '4px solid #3b82f6' }}>
                            <Typography color="text.secondary">Orphan Files</Typography>
                            <Typography variant="h3" fontWeight="bold" color="text.primary">
                                {status.orphanBlobs}
                            </Typography>
                        </Paper>
                    </Grid>
                </Grid>
            )}

            <Paper className="glass-panel" sx={{ borderRadius: 3 }}>
                <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
                    <Tabs value={tabValue} onChange={handleTabChange} aria-label="integrity details tabs">
                        <Tab label="Missing Blobs" icon={<ErrorIcon fontSize="small" color="error" />} iconPosition="start" />
                        <Tab label="Corrupt Files" icon={<WarningIcon fontSize="small" color="warning" />} iconPosition="start" />
                        <Tab label="Orphan Files" icon={<QuestionMarkIcon fontSize="small" color="info" />} iconPosition="start" />
                    </Tabs>
                </Box>

                <Box p={3}>
                    {loadingDetails ? (
                        <Typography>Loading details...</Typography>
                    ) : (
                        <>
                            <Typography variant="subtitle2" gutterBottom color="text.secondary">
                                Showing top 100 {detailsType} items found during last scan
                            </Typography>
                            <Paper variant="outlined" sx={{ maxHeight: 400, overflow: 'auto', bgcolor: 'rgba(0,0,0,0.2)' }}>
                                <List dense>
                                    {details.map((item, index) => (
                                        <div key={index}>
                                            <ListItem>
                                                <ListItemText
                                                    primary={item}
                                                    primaryTypographyProps={{ fontFamily: 'monospace', fontSize: '0.9rem' }}
                                                />
                                            </ListItem>
                                            <Divider component="li" />
                                        </div>
                                    ))}
                                    {details.length === 0 && (
                                        <ListItem>
                                            <ListItemText primary="No items found." />
                                        </ListItem>
                                    )}
                                </List>
                            </Paper>
                        </>
                    )}
                </Box>
            </Paper>
        </Box>
    );
}
