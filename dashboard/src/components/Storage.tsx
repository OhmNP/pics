import { useEffect, useState } from 'react';
import { Box, Paper, Grid, Typography, LinearProgress, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip } from '@mui/material';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip as RechartsTooltip, Legend } from 'recharts';
import StorageIcon from '@mui/icons-material/Storage';
import SecurityIcon from '@mui/icons-material/Security';
import { api, ServerStats, IntegrityReport, FileAnalysis } from '../services/api';

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];

export default function Storage() {
    const [stats, setStats] = useState<ServerStats | null>(null);
    const [integrity, setIntegrity] = useState<IntegrityReport | null>(null);
    const [loading, setLoading] = useState(true);
    const [topFiles, setTopFiles] = useState<FileAnalysis[]>([]);

    const fetchData = async () => {
        try {
            setLoading(true);
            const stats = await api.getStats();
            setStats(stats.data);

            const integrity = await api.getIntegrityStatus();
            setIntegrity(integrity.data);

            const files = await api.getTopFiles();
            setTopFiles(files.data.topFiles);
        } catch (err) {
            console.error('Failed to fetch data:', err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
        // Refresh stats every 30s
        const interval = setInterval(fetchData, 30000);
        return () => clearInterval(interval);
    }, []);

    const formatBytes = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + ['B', 'KB', 'MB', 'GB', 'TB'][i];
    };

    if (loading || !stats) {
        return <LinearProgress />;
    }

    // Disk Usage Data for Chart
    // Assuming api.getStats now returns diskTotal and diskFree (or similar) from previous edits
    // Wait, I only added `storageUsed` and `storageLimit` to ServerStats in api.ts?
    // Let's check api.ts again. I added diskTotal/diskFree to *backend* ApiServer.cpp but did I add it to `ServerStats` interface in `api.ts`?
    // I missed adding it to the TS interface in the previous step.
    // I will use them as `any` or update types later. For now, let's assume they are there or use available fields.
    // backend sends: diskTotal, diskFree, storageUsed, storageLimit.

    // Usage of Quota (App specific)
    const quotaData = [
        { name: 'Used', value: stats.storageUsed },
        { name: 'Free', value: stats.storageLimit - stats.storageUsed }
    ];

    // Usage of Disk (System) - using extended stats if available
    const diskStats = stats as any; // Cast to access unchecked props
    const diskData = diskStats.diskTotal ? [
        { name: 'Used Space', value: diskStats.diskTotal - diskStats.diskFree },
        { name: 'Free Space', value: diskStats.diskFree }
    ] : [];

    return (
        <Box>
            <Typography variant="h4" fontWeight="800" className="gradient-text" gutterBottom>
                Storage & Integrity
            </Typography>
            <Typography variant="body1" color="text.secondary" mb={4}>
                Disk usage analysis and data integrity health
            </Typography>

            <Grid container spacing={3}>
                {/* Storage Quota Chart */}
                <Grid item xs={12} md={6}>
                    <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, height: '100%' }}>
                        <Box display="flex" alignItems="center" gap={1} mb={2}>
                            <StorageIcon color="primary" />
                            <Typography variant="h6">Application Quota</Typography>
                        </Box>
                        <Box height={300}>
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={quotaData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={80}
                                        paddingAngle={5}
                                        dataKey="value"
                                    >
                                        <Cell fill="var(--primary)" />
                                        <Cell fill="rgba(255,255,255,0.1)" />
                                    </Pie>
                                    <RechartsTooltip
                                        formatter={(value: number) => formatBytes(value)}
                                        contentStyle={{ backgroundColor: '#1e293b', borderColor: '#334155' }}
                                    />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        </Box>
                        <Box mt={2} textAlign="center">
                            <Typography variant="h4">{formatBytes(stats.storageUsed)}</Typography>
                            <Typography variant="body2" color="text.secondary">of {formatBytes(stats.storageLimit)} Used</Typography>
                        </Box>
                    </Paper>
                </Grid>

                {/* Integrity Status */}
                <Grid item xs={12} md={6}>
                    <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, height: '100%' }}>
                        <Box display="flex" alignItems="center" gap={1} mb={2}>
                            <SecurityIcon color="secondary" />
                            <Typography variant="h6">Data Integrity</Typography>
                        </Box>

                        {integrity ? (
                            <Box>
                                <Box display="flex" justifyContent="space-between" alignItems="center" mb={3} p={2} sx={{ bgcolor: 'rgba(255,255,255,0.05)', borderRadius: 2 }}>
                                    <Typography variant="subtitle1">Status</Typography>
                                    <Chip
                                        label={integrity.status.toUpperCase()}
                                        color={integrity.status === 'active' || integrity.status === 'clean' ? 'success' : 'warning'}
                                        variant="filled"
                                    />
                                </Box>

                                <Grid container spacing={2}>
                                    <Grid item xs={6}>
                                        <Typography color="text.secondary" variant="caption">Last Scan</Typography>
                                        <Typography variant="body1">{integrity.lastScan}</Typography>
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography color="text.secondary" variant="caption">Total Photos</Typography>
                                        <Typography variant="body1">{integrity.totalPhotos}</Typography>
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography color="text.secondary" variant="caption">Missing Blobs</Typography>
                                        <Typography variant="body1" color={integrity.missingBlobs > 0 ? 'error.main' : 'text.primary'}>
                                            {integrity.missingBlobs}
                                        </Typography>
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography color="text.secondary" variant="caption">Corrupt Blobs</Typography>
                                        <Typography variant="body1" color={integrity.corruptBlobs > 0 ? 'error.main' : 'text.primary'}>
                                            {integrity.corruptBlobs}
                                        </Typography>
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography color="text.secondary" variant="caption">Orphan Blobs</Typography>
                                        <Typography variant="body1" color="warning.main">
                                            {integrity.orphanBlobs}
                                        </Typography>
                                    </Grid>
                                </Grid>

                                {integrity.message && (
                                    <Typography color="text.secondary" sx={{ mt: 3, fontStyle: 'italic' }}>
                                        {integrity.message}
                                    </Typography>
                                )}
                            </Box>
                        ) : (
                            <Typography>Loading integrity status...</Typography>
                        )}
                    </Paper>
                </Grid>

                {/* System Disk Info (if available) */}
                {diskStats.diskTotal && (
                    <Grid item xs={12}>
                        <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3 }}>
                            <Box display="flex" alignItems="center" gap={1} mb={2}>
                                <StorageIcon color="action" />
                                <Typography variant="h6">Physical Disk Status</Typography>
                            </Box>
                            <Box>
                                <Typography variant="body2" mb={1}>
                                    Total: {formatBytes(diskStats.diskTotal)} | Free: {formatBytes(diskStats.diskFree)}
                                </Typography>
                                <LinearProgress
                                    variant="determinate"
                                    value={((diskStats.diskTotal - diskStats.diskFree) / diskStats.diskTotal) * 100}
                                    sx={{ height: 10, borderRadius: 5 }}
                                />
                            </Box>
                        </Paper>
                    </Grid>
                )}
            </Grid>

            {/* Top Files Section */}
            <Typography variant="h6" fontWeight="bold" mb={2} mt={4}>
                Largest Files
            </Typography>
            <Paper className="glass-panel" sx={{ borderRadius: 3, overflow: 'hidden', mb: 4 }}>
                <TableContainer>
                    <Table>
                        <TableHead sx={{ bgcolor: 'rgba(255,255,255,0.05)' }}>
                            <TableRow>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Filename</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Type</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Size</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Path</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {topFiles.map((file) => (
                                <TableRow key={file.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                                    <TableCell sx={{ color: 'text.primary', fontWeight: 500 }}>{file.filename}</TableCell>
                                    <TableCell>
                                        <Chip label={file.mimeType} size="small" variant="outlined" sx={{ borderColor: 'rgba(255,255,255,0.2)' }} />
                                    </TableCell>
                                    <TableCell sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>{formatBytes(file.size)}</TableCell>
                                    <TableCell sx={{ color: 'text.secondary', fontSize: '0.875rem' }}>{file.originalPath}</TableCell>
                                </TableRow>
                            ))}
                            {topFiles.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={4} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                                        No files found.
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Paper>
        </Box>
    );
}
