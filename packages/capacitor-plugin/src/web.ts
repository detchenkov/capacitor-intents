/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-unused-vars */
import { WebPlugin } from '@capacitor/core';

import type { CapacitorIntentsPlugin } from './definitions';

export class CapacitorIntentsPluginWeb extends WebPlugin implements CapacitorIntentsPlugin {
  async registerBroadcastReceiver(
    _options: { filters: string[]; categories?: string[] },
    _callback: (data: { [key: string]: any }) => void
  ): Promise<string> {
    throw new Error('Feature not implemented in web.');
  }

  async unregisterBroadcastReceiver(_options: { id: string }) {
    throw new Error('Feature not implemented in web.');
  }

  async sendBroadcastIntent(_options: { action: string; extras: { [key: string]: any } }): Promise<void> {
    throw new Error('Feature not implemented in web.');
  }
}
