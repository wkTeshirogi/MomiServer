import { type ReactNode } from 'react'
import { AlertTriangle, Info, CheckCircle, Trash2 } from 'lucide-react'

// Modal presentation definitions remain within components/ui.
export type ModalVariant = 'default' | 'info' | 'success' | 'warning' | 'danger'

export type ModalSize    = 'sm' | 'md' | 'lg' | 'xl'

export const variantConfig: Record<ModalVariant, {
  icon: ReactNode
  headerClass: string
  iconClass: string
}> = {
  default: {
    icon: null,
    headerClass: '',
    iconClass:   '',
  },
  info: {
    icon:        <Info size={18} />,
    headerClass: 'text-blue-500 dark:text-blue-400',
    iconClass:   'bg-blue-500/10 text-blue-500 dark:text-blue-400',
  },
  success: {
    icon:        <CheckCircle size={18} />,
    headerClass: 'text-green-500 dark:text-green-400',
    iconClass:   'bg-green-500/10 text-green-500 dark:text-green-400',
  },
  warning: {
    icon:        <AlertTriangle size={18} />,
    headerClass: 'text-amber-500 dark:text-amber-400',
    iconClass:   'bg-amber-500/10 text-amber-500 dark:text-amber-400',
  },
  danger: {
    icon:        <Trash2 size={18} />,
    headerClass: 'text-red-500 dark:text-red-400',
    iconClass:   'bg-red-500/10 text-red-500 dark:text-red-400',
  },
}

export const sizeClass: Record<ModalSize, string> = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-lg',
  xl: 'max-w-xl',
}
