import { ThemeProvider, CssBaseline, Box, Drawer, List, ListItem, ListItemIcon, ListItemText, ListItemButton, Toolbar, Typography } from '@mui/material';
import { BrowserRouter, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import { theme } from './theme/theme';
import Dashboard from './components/Dashboard';
import Photos from './components/Photos';
import Clients from './components/Clients';
import Sessions from './components/Sessions';
import Settings from './components/Settings';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PhotoLibraryIcon from '@mui/icons-material/PhotoLibrary';
import PeopleIcon from '@mui/icons-material/People';
import SyncIcon from '@mui/icons-material/Sync';
import SettingsIcon from '@mui/icons-material/Settings';

const drawerWidth = 240;

interface NavItem {
    text: string;
    icon: React.ReactNode;
    path: string;
}

const navItems: NavItem[] = [
    { text: 'Dashboard', icon: <DashboardIcon />, path: '/' },
    { text: 'Photos', icon: <PhotoLibraryIcon />, path: '/photos' },
    { text: 'Clients', icon: <PeopleIcon />, path: '/clients' },
    { text: 'Sessions', icon: <SyncIcon />, path: '/sessions' },
    { text: 'Settings', icon: <SettingsIcon />, path: '/settings' },
];

function Navigation() {
    const navigate = useNavigate();
    const location = useLocation();

    return (
        <Drawer
            variant="permanent"
            sx={{
                width: drawerWidth,
                flexShrink: 0,
                '& .MuiDrawer-paper': {
                    width: drawerWidth,
                    boxSizing: 'border-box',
                    backgroundColor: 'background.default',
                    borderRight: '1px solid rgba(255, 255, 255, 0.1)',
                },
            }}
        >
            <Toolbar>
                <Typography variant="h6" fontWeight="bold">
                    PhotoSync
                </Typography>
            </Toolbar>
            <List>
                {navItems.map((item) => (
                    <ListItem key={item.text} disablePadding>
                        <ListItemButton
                            selected={location.pathname === item.path}
                            onClick={() => navigate(item.path)}
                            sx={{
                                '&.Mui-selected': {
                                    backgroundColor: 'rgba(0, 217, 255, 0.1)',
                                    borderRight: '3px solid #00d9ff',
                                },
                            }}
                        >
                            <ListItemIcon sx={{ color: location.pathname === item.path ? '#00d9ff' : 'inherit' }}>
                                {item.icon}
                            </ListItemIcon>
                            <ListItemText primary={item.text} />
                        </ListItemButton>
                    </ListItem>
                ))}
            </List>
        </Drawer>
    );
}

function AppContent() {
    return (
        <Box sx={{ display: 'flex' }}>
            <CssBaseline />
            <Navigation />
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    minHeight: '100vh',
                    backgroundColor: 'background.default',
                }}
            >
                <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/photos" element={<Photos />} />
                    <Route path="/clients" element={<Clients />} />
                    <Route path="/sessions" element={<Sessions />} />
                    <Route path="/settings" element={<Settings />} />
                </Routes>
            </Box>
        </Box>
    );
}

export default function App() {
    return (
        <ThemeProvider theme={theme}>
            <BrowserRouter>
                <AppContent />
            </BrowserRouter>
        </ThemeProvider>
    );
}
