import ScreenShareModule from './src/ScreenShareModule';

export async function startHttpServer(): Promise<string> {
  return await ScreenShareModule.startServerAsync();
}