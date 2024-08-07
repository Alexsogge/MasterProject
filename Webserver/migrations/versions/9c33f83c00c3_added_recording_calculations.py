"""added recording calculations

Revision ID: 9c33f83c00c3
Revises: f2525e71bdbb
Create Date: 2022-01-24 10:16:16.476786

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = '9c33f83c00c3'
down_revision = 'f2525e71bdbb'
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.create_table('recording_calculations',
    sa.Column('id', sa.Integer(), nullable=False),
    sa.Column('variance', sa.String(length=1024), nullable=True),
    sa.PrimaryKeyConstraint('id')
    )
    with op.batch_alter_table('recording', schema=None) as batch_op:
        batch_op.add_column(sa.Column('calculations_id', sa.Integer(), nullable=True))
        batch_op.create_foreign_key('calculations', 'recording_calculations', ['calculations_id'], ['id'])

    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('recording', schema=None) as batch_op:
        batch_op.drop_constraint(None, type_='foreignkey')
        batch_op.drop_column('calculations_id')

    op.drop_table('recording_calculations')
    # ### end Alembic commands ###
