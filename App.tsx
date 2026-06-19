/**
 * Hermes Chat App — Root with bottom tab navigation.
 * Navy Amber theme. Three tabs: Chat, Terminal, Settings.
 * Connects via HTTP streaming directly to Hermes API server (port 8650).
 */
import React, { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Text, View, Platform } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import ChatScreen from './src/screens/ChatScreen';
import TerminalScreen from './src/screens/TerminalScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import { getHermesAPI } from './src/services/api';
import { setupNotifications } from './src/services/notifications';
import { detectNetwork } from './src/utils/network';

const Tab = createBottomTabNavigator();

function TabIcon({ label }: { label: string; focused: boolean }) {
  const icons: Record<string, string> = {
    Chat: '💬',
    Terminal: '⌨️',
    Settings: '⚙️',
  };
  return (
    <View style={{ alignItems: 'center' }}>
      <Text style={{ fontSize: 20 }}>{icons[label] || '📱'}</Text>
    </View>
  );
}

export default function App() {
  useEffect(() => {
    let cleanupNotifications: (() => void) | undefined;
    let isMounted = true;

    const initializeApp = async () => {
      try {
        const cleanup = await setupNotifications();
        if (isMounted) {
          cleanupNotifications = cleanup;
        } else {
          cleanup?.();
        }
      } catch (error) {
        console.warn('Notification initialization failed:', error);
      }

      try {
        const info = await detectNetwork();
        if (isMounted) {
          await getHermesAPI().connect(info.host);
        }
      } catch (error) {
        console.warn('Hermes API initialization failed:', error);
      }
    };

    initializeApp().catch(error => {
      console.warn('Unexpected app initialization failure:', error);
    });

    return () => {
      isMounted = false;
      try {
        cleanupNotifications?.();
      } catch (error) {
        console.warn('Notification cleanup failed:', error);
      }
      try {
        getHermesAPI().disconnect();
      } catch (error) {
        console.warn('Hermes API disconnect failed:', error);
      }
    };
  }, []);

  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <NavigationContainer
        theme={{
          dark: true,
          colors: {
            primary: '#B8860B',
            background: '#0a0a1a',
            card: '#111128',
            text: '#e0e0e0',
            border: '#1a1a2e',
            notification: '#B8860B',
          },
          fonts: {
            regular: { fontFamily: Platform.OS === 'ios' ? 'System' : 'Roboto', fontWeight: '400' as const },
            medium: { fontFamily: Platform.OS === 'ios' ? 'System' : 'Roboto', fontWeight: '500' as const },
            bold: { fontFamily: Platform.OS === 'ios' ? 'System' : 'Roboto', fontWeight: '700' as const },
            heavy: { fontFamily: Platform.OS === 'ios' ? 'System' : 'Roboto', fontWeight: '900' as const },
          },
        }}
      >
        <Tab.Navigator
          screenOptions={({ route }) => ({
            tabBarIcon: ({ focused }) => <TabIcon label={route.name} focused={focused} />,
            tabBarHideOnKeyboard: Platform.OS === 'android',
            tabBarActiveTintColor: '#B8860B',
            tabBarInactiveTintColor: '#667788',
            tabBarStyle: {
              backgroundColor: '#111128',
              borderTopColor: '#1a1a2e',
              borderTopWidth: 1,
              paddingBottom: 4,
              paddingTop: 4,
              height: 60,
            },
            tabBarLabelStyle: {
              fontSize: 11,
              fontWeight: '600',
            },
            headerStyle: {
              backgroundColor: '#111128',
            },
            headerTintColor: '#e0c070',
            headerTitleStyle: {
              fontWeight: 'bold',
            },
          })}
        >
          <Tab.Screen name="Chat" component={ChatScreen}
            options={{ title: 'Hermes', headerStyle: { backgroundColor: '#111128', elevation: 0, shadowOpacity: 0 } }} />
          <Tab.Screen name="Terminal" component={TerminalScreen}
            options={{ headerStyle: { backgroundColor: '#0a0a0a', elevation: 0, shadowOpacity: 0 } }} />
          <Tab.Screen name="Settings" component={SettingsScreen}
            options={{ headerStyle: { backgroundColor: '#0a0a1a', elevation: 0, shadowOpacity: 0 } }} />
        </Tab.Navigator>
      </NavigationContainer>
    </SafeAreaProvider>
  );
}
