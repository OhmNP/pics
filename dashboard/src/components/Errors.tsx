import { useEffect, useState } from 'react';
import { Box, Typography, Paper, Grid, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip, IconButton, Tooltip } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { api, ErrorLog } from '../services/api';

export default function Errors() {
    const [errors, setErrors] = useState<ErrorLog[]>([]);
    const [loading, setLoading] = useState(false);

    const [level, setLevel] = useState('');
    const [deviceId, setDeviceId] = useState('');
    const [limit, setLimit] = useState(50);

    const fetchErrors = async () => {
        setLoading(true);
        try {
            const response = await api.getErrors(limit, 0, level, deviceId);
            setErrors(response.data.errors);
        } catch (err) {
            console.error('Failed to fetch errors:', err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchErrors();
    }, [level, deviceId, limit]);

    return (
        <Box>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
                <div>
                    <Typography variant="h4" fontWeight="800" className="gradient-text" gutterBottom>
                        System Errors
                    </Typography>
                    <Typography variant="body1" color="text.secondary">
                        Recent 50 error logs from the server
                    </Typography>
                </div>
                <IconButton onClick={fetchErrors} disabled={loading} sx={{ color: 'var(--primary)' }}>
                    <RefreshIcon />
                </IconButton>
            </Box>

            {/* Filters */}
            <Paper className="glass-panel" sx={{ p: 2, mb: 4, display: 'flex', gap: 2, alignItems: 'center' }}>
                <Typography fontWeight="600" mr={1}>Filter:</Typography>
                <select
                    style={{ padding: '8px', borderRadius: '8px', background: 'rgba(255,255,255,0.05)', color: 'white', border: '1px solid rgba(255,255,255,0.1)' }}
                    value={level}
                    onChange={(e) => setLevel(e.target.value)}
                >
                    <option value="">All Levels</option>
                    <option value="ERROR">ERROR</option>
                    <option value="WARN">WARN</option>
                    <option value="INFO">INFO</option>
                    <option value="CRITICAL">CRITICAL</option>
                </select>

                <input
                    type="text"
                    placeholder="Device ID"
                    value={deviceId}
                    onChange={(e) => setDeviceId(e.target.value)}
                    style={{ padding: '8px', borderRadius: '8px', background: 'rgba(255,255,255,0.05)', color: 'white', border: '1px solid rgba(255,255,255,0.1)' }}
                />

                <select
                    style={{ padding: '8px', borderRadius: '8px', background: 'rgba(255,255,255,0.05)', color: 'white', border: '1px solid rgba(255,255,255,0.1)' }}
                    value={limit}
                    onChange={(e) => setLimit(Number(e.target.value))}
                >
                    <option value={50}>50 rows</option>
                    <option value={100}>100 rows</option>
                    <option value={500}>500 rows</option>
                </select>
            </Paper>

            <Grid container spacing={3} mb={4}>
                <Grid item xs={12} sm={4}>
                    <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3, background: 'linear-gradient(135deg, rgba(255, 99, 71, 0.1), rgba(255, 99, 71, 0.05))' }}>
                        <Typography variant="body2" color="text.secondary">Total Recent Errors</Typography>
                        <Typography variant="h3" fontWeight="800" sx={{ color: 'var(--accent-red)', mt: 1 }}>{errors.length}</Typography>
                    </Paper>
                </Grid>
                <Grid item xs={12} sm={8}>
                    <Paper className="glass-panel" sx={{ p: 3, borderRadius: 3 }}>
                        <Typography variant="body2" color="text.secondary" mb={2}>Error Distribution</Typography>
                        <Box display="flex" gap={2} flexWrap="wrap">
                            {Object.entries(errors.reduce((acc, curr) => {
                                acc[curr.code] = (acc[curr.code] || 0) + 1;
                                return acc;
                            }, {} as Record<string, number>)).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([code, count]) => (
                                <Box key={code} sx={{ mr: 4 }}>
                                    <Typography variant="h5" fontWeight="700">{count}</Typography>
                                    <Typography variant="caption" color="text.secondary">{code}</Typography>
                                </Box>
                            ))}
                        </Box>
                    </Paper>
                </Grid>
            </Grid>

            <TableContainer component={Paper} className="glass-panel" sx={{ borderRadius: 3, overflow: 'hidden' }}>
                <Table>
                    <TableHead sx={{ bgcolor: 'rgba(255,255,255,0.05)' }}>
                        <TableRow>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Severity</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Timestamp</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Code</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Message</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Device</TableCell>
                            <TableCell sx={{ color: 'text.secondary', fontWeight: 600 }}>Context</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {errors.map((error) => (
                            <TableRow key={error.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                                <TableCell sx={{ color: 'text.primary' }}>
                                    <Chip
                                        label={error.severity || 'ERROR'}
                                        size="small"
                                        color={error.severity === 'CRITICAL' ? 'error' : error.severity === 'WARN' ? 'warning' : error.severity === 'INFO' ? 'info' : 'error'}
                                        variant="filled"
                                    />
                                </TableCell>
                                <TableCell sx={{ color: 'text.secondary' }}>{new Date(error.timestamp).toLocaleString()}</TableCell>
                                <TableCell>
                                    <Chip
                                        label={error.code}
                                        size="small"
                                        variant="outlined"
                                        sx={{ borderColor: 'rgba(255,255,255,0.2)' }}
                                    />
                                </TableCell>
                                <TableCell sx={{ color: 'text.primary', fontWeight: 500 }}>{error.message}</TableCell>
                                <TableCell sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>{error.deviceId || '-'}</TableCell>
                                <TableCell>
                                    {error.context ? (
                                        <Tooltip title={error.context}>
                                            <Chip label="Context" size="small" />
                                        </Tooltip>
                                    ) : '-'}
                                </TableCell>
                            </TableRow>
                        ))}
                        {errors.length === 0 && !loading && (
                            <TableRow>
                                <TableCell colSpan={5} align="center" sx={{ py: 8 }}>
                                    <ErrorOutlineIcon sx={{ fontSize: 48, color: 'text.secondary', opacity: 0.5, mb: 2 }} />
                                    <Typography color="text.secondary">No errors found.</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Box>
    );
}
