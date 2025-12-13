import React, { useEffect, useState } from 'react';
import {
    Box,
    Card,
    CardContent,
    Container,
    Typography,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
    Chip,
    IconButton,
    Tooltip,
} from '@mui/material';
import { Refresh as RefreshIcon, ErrorOutline as ErrorIcon, InfoOutlined as InfoIcon, WarningAmber as WarningIcon } from '@mui/icons-material';
import axios from 'axios';

interface LogEntry {
    id: number;
    level: string;
    message: string;
    timestamp: string;
    context: string;
    read: boolean;
}

const getLevelColor = (level: string) => {
    switch (level) {
        case 'FATAL': return 'error';
        case 'ERROR': return 'error';
        case 'WARNING': return 'warning';
        case 'INFO': return 'info';
        default: return 'default';
    }
};

const getLevelIcon = (level: string) => {
    switch (level) {
        case 'FATAL': return <ErrorIcon color="error" />;
        case 'ERROR': return <ErrorIcon color="error" />;
        case 'WARNING': return <WarningIcon color="warning" />;
        default: return <InfoIcon color="info" />;
    }
}

const ErrorFeed: React.FC = () => {
    const [logs, setLogs] = useState<LogEntry[]>([]);
    const [loading, setLoading] = useState<boolean>(true);

    const fetchLogs = async () => {
        setLoading(true);
        try {
            const response = await axios.get('/api/logs');
            setLogs(response.data);
        } catch (err) {
            console.error('Failed to fetch logs', err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLogs();
        const interval = setInterval(fetchLogs, 10000); // Poll every 10s
        return () => clearInterval(interval);
    }, []);

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
                <Typography variant="h4">System Logs</Typography>
                <IconButton onClick={fetchLogs} disabled={loading}>
                    <RefreshIcon />
                </IconButton>
            </Box>

            <TableContainer component={Paper} className="glass-panel">
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>Level</TableCell>
                            <TableCell>Message</TableCell>
                            <TableCell>Timestamp</TableCell>
                            <TableCell>Context</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {logs.length === 0 ? (
                            <TableRow>
                                <TableCell colSpan={4} align="center">No logs found</TableCell>
                            </TableRow>
                        ) : (
                            logs.map((log) => (
                                <TableRow key={log.id} sx={{ backgroundColor: log.level === 'FATAL' || log.level === 'ERROR' ? 'rgba(211, 47, 47, 0.05)' : 'inherit' }}>
                                    <TableCell>
                                        <Chip
                                            icon={getLevelIcon(log.level)}
                                            label={log.level}
                                            color={getLevelColor(log.level) as any}
                                            size="small"
                                            variant="outlined"
                                        />
                                    </TableCell>
                                    <TableCell>{log.message}</TableCell>
                                    <TableCell>{log.timestamp}</TableCell>
                                    <TableCell>
                                        {log.context && (
                                            <Tooltip title={log.context}>
                                                <Typography variant="caption" sx={{ cursor: 'pointer', textDecoration: 'underline' }}>
                                                    View
                                                </Typography>
                                            </Tooltip>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </TableContainer>
        </Container>
    );
};

export default ErrorFeed;
