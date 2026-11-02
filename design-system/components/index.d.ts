import type * as React from 'react';
export type CodeKind = 'web' | 'youtube' | 'store' | 'app' | 'wifi' | 'bluetooth' | 'unsupported';
export type StatusKind = 'positive' | 'warning' | 'negative';
export type IconName = 'scan' | 'check' | 'warning' | 'lock' | 'camera' | 'internet' | 'youtube' | 'store' | 'app' | 'wifi' | 'bluetooth' | 'unsupported' | 'stop' | 'play' | 'open' | 'positive' | 'negative';
export interface PillReaderProps {
  /** scanning: tracker on · paused: idle cap reached · off: stopped · status: one line about OpenQR itself */
  mode?: 'scanning' | 'paused' | 'off' | 'status';
  status?: { kind?: StatusKind | 'opening'; title: string; action?: { label: string } };
  onToggleScan?: () => void;
  onAction?: () => void;
}
export declare function PillReader(props: PillReaderProps): React.ReactElement;
export interface CodeTagProps {
  state?: 'found' | 'hover' | 'opening' | 'unsupported' | 'lost';
  kind?: CodeKind; label?: string;
  /** Bracket box size in px at panel scale; match the code's bounds when known. */
  size?: number;
  onClick?: () => void;
}
export declare function CodeTag(props: CodeTagProps): React.ReactElement;
export interface Notice { id?: string | number; kind?: CodeKind; label?: string; state?: 'inView' | 'lingering' | 'opening' | 'unsupported' }
export interface NoticeStackProps { notices?: Notice[]; max?: number; onOpen?: (notice: Notice, index: number) => void }
/** Fallback without Spatial Data: decoded codes as notices stacked above the pill. */
export declare function NoticeStack(props: NoticeStackProps): React.ReactElement;
export interface ScanToggleProps { on?: boolean; label?: string; onClick?: () => void }
export declare function ScanToggle(props: ScanToggleProps): React.ReactElement;
export interface ActionButtonProps { variant?: 'primary' | 'quiet'; active?: boolean; expanded?: boolean; disabled?: boolean; icon?: IconName; onClick?: () => void; children?: React.ReactNode }
export declare function ActionButton(props: ActionButtonProps): React.ReactElement;
export interface CodeChipProps { opening?: boolean; kind?: CodeKind; label?: string; variant?: 'onCode' | 'row'; inView?: boolean; enabled?: boolean; onClick?: () => void }
export declare function CodeChip(props: CodeChipProps): React.ReactElement;
export interface IconProps { name: IconName; size?: number; title?: string; className?: string }
export declare function Icon(props: IconProps): React.ReactElement;
declare global { interface Window { OpenQR: { PillReader: typeof PillReader; CodeTag: typeof CodeTag; NoticeStack: typeof NoticeStack; ScanToggle: typeof ScanToggle; ActionButton: typeof ActionButton; CodeChip: typeof CodeChip; Icon: typeof Icon } } }
