
import React, { useEffect, useState } from 'react';
import {
    Box,
    Card,
    CardContent,
    Container,
    Grid,
    Typography,
    Chip,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
    Button,
} from '@mui/material';
import {
    ArrowBack as ArrowBackIcon,
    Smartphone as SmartphoneIcon,
} from '@mui/icons-material';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

interface ClientDetailsData {
    id: number;
    deviceId: string;
    lastSeen: string;
    photoCount: number;
    storageUsed: number;
    formattedStorage: number;
}

interface Session {
    id: number;
    clientId: number;
    startedAt: string;
    endedAt: string;
    photosReceived: number;
    status: string;
}

const ClientDetails: React.FC = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [client, setClient] = useState<ClientDetailsData | null>(null);
    const [sessions, setSessions] = useState<Session[]>([]);
    const [loading, setLoading] = useState<boolean>(true);

    useEffect(() => {
        const fetchData = async () => {
            try {
                const [clientRes, sessionsRes] = await Promise.all([
                    axios.get(`/api/clients/${id}`),
                    axios.get(`/api/sessions?client_id=${id}&limit=10`)
                ]);
                setClient(clientRes.data);
                setSessions(sessionsRes.data);
            } catch (err) {
                console.error("Failed to fetch client details", err);
            } finally {
                setLoading(false);
            }
        };

        if (id) {
            fetchData();
        }
    }, [id]);

    const formatBytes = (bytes: number) => {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    if (loading) return <Typography>Loading...</Typography>;
    if (!client) return <Typography>Client not found</Typography>;

    return (
        <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
            <Button
                startIcon={<ArrowBackIcon />}
                onClick={() => navigate('/clients')}
                sx={{ mb: 2 }}
            >
                Back to Clients
            </Button>

            <Card sx={{ mb: 4 }}>
                <CardContent>
                    <Box display="flex" alignItems="center" mb={2}>
                        <SmartphoneIcon color="primary" sx={{ mr: 2, fontSize: 40 }} />
                        <Typography variant="h4">{client.deviceId}</Typography>
                    </Box>
                    <Grid container spacing={3}>
                        <Grid item xs={12} sm={4}>
                            <Typography variant="subtitle2" color="text.secondary">Total Photos</Typography>
                            <Typography variant="h6">{client.photoCount}</Typography>
                        </Grid>
                        <Grid item xs={12} sm={4}>
                            <Typography variant="subtitle2" color="text.secondary">Storage Used</Typography>
                            <Typography variant="h6">{formatBytes(client.storageUsed)}</Typography>
                        </Grid>
                        <Grid item xs={12} sm={4}>
                            <Typography variant="subtitle2" color="text.secondary">Last Seen</Typography>
                            <Typography variant="h6">{client.lastSeen}</Typography>
                        </Grid>
                    </Grid>
                </CardContent>
            </Card>

            <Typography variant="h5" sx={{ mb: 2 }}>Recent Sync Sessions</Typography>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>ID</TableCell>
                            <TableCell>Started At</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell align="right">Photos</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {sessions.map((session) => (
                            <TableRow key={session.id}>
                                <TableCell>{session.id}</TableCell>
                                <TableCell>{session.startedAt}</TableCell>
                                <TableCell>
                                    <Chip
                                        label={session.status}
                                        color={session.status === 'COMPLETED' ? 'success' : 'default'}
                                        size="small"
                                    />
                                </TableCell>
                                <TableCell align="right">{session.photosReceived}</TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
        </Container>
    );
};

export default ClientDetails;
